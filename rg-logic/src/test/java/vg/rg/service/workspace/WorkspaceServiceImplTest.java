package vg.rg.service.workspace;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;
import vg.rg.exception.workspace.WorkspaceLimitReachedException;
import vg.rg.exception.workspace.WorkspaceNotRemovableException;
import vg.rg.mapper.workspace.WorkspaceMapper;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceServiceImplTest {

    private static final UniqueId OWNER = new UniqueId(4001L);
    // Deliberately not the production defaults (20/128/1024): only non-default bounds prove the
    // configured values actually reach the service rather than being hard-coded in it.
    private static final int MAX_PER_USER = 3;
    private static final int NAME_MAX_LENGTH = 32;
    private static final int DESCRIPTION_MAX_LENGTH = 64;

    @Mock
    private UniqueIdService uniqueIdService;
    @Mock
    private WorkspaceRepository repository;
    @Mock
    private WorkspaceMapper mapper;
    @Mock
    private AuthorityChecker authorityChecker;
    @Mock
    private WorkspaceSelectionRepository selectionRepository;
    @Mock
    private WorkspaceContentContributor contributor;

    private WorkspaceServiceImpl service;

    @BeforeEach
    void setUp() {
        var properties = WorkspaceProperties.builder()
                .maxPerUser(String.valueOf(MAX_PER_USER))
                .nameMaxLength(String.valueOf(NAME_MAX_LENGTH))
                .descriptionMaxLength(String.valueOf(DESCRIPTION_MAX_LENGTH))
                .build();
        service = new WorkspaceServiceImpl(
                uniqueIdService, repository, mapper, properties, authorityChecker,
                selectionRepository, List.of(contributor));
        when(contributor.resourceType()).thenReturn("LOCATION");
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(OWNER));
        when(mapper.toModel(any())).thenAnswer(invocation -> {
            WorkspaceEntity entity = invocation.getArgument(0);
            return WorkspaceModel.builder()
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .defaultWorkspace(entity.isDefaultWorkspace())
                    .build();
        });
        when(repository.saveWithNewUniqueId(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------------------------- create

    @Test
    void create_storesTheUserSuppliedName() {
        var created = service.create(WorkspaceModel.builder().name("  Field work  ").build());

        assertThat(created.getName()).isEqualTo("Field work");
        assertThat(created.isDefaultWorkspace()).isFalse();
    }

    @Test
    void create_neverProducesADefaultWorkspace() {
        // Only ensureDefault may set the default marker; otherwise a user could create a second default
        // and collide with their own auto-provisioned one.
        service.create(WorkspaceModel.builder().name("Second").build());

        verify(repository).saveWithNewUniqueId(
                org.mockito.ArgumentMatchers.argThat(entity -> entity.getDefaultForOwner() == null),
                any());
    }

    @Test
    void create_blankOrMissingName_isRejected() {
        assertThatThrownBy(() -> service.create(WorkspaceModel.builder().name("   ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceServiceImpl.WORKSPACE_NAME_REQUIRED);
        assertThatThrownBy(() -> service.create(WorkspaceModel.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceServiceImpl.WORKSPACE_NAME_REQUIRED);
    }

    @Test
    void create_overlongName_isRejected() {
        var tooLong = "x".repeat(NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.create(WorkspaceModel.builder().name(tooLong).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceServiceImpl.WORKSPACE_NAME_TOO_LONG);
    }

    @Test
    void create_overlongDescription_isRejected() {
        var model = WorkspaceModel.builder().name("Fine").description("y".repeat(DESCRIPTION_MAX_LENGTH + 1)).build();

        assertThatThrownBy(() -> service.create(model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceServiceImpl.WORKSPACE_DESCRIPTION_TOO_LONG);
    }

    @Test
    void create_blankDescription_isStoredAsAbsent() {
        var created = service.create(
                WorkspaceModel.builder().name("Fine").description("   ").build());

        assertThat(created.getDescription()).isNull();
    }

    @Test
    void create_atTheWorkspaceLimit_isRefusedWithTheLimit() {
        when(repository.countByOwnerUniqueId(OWNER)).thenReturn((long) MAX_PER_USER);

        assertThatThrownBy(() -> service.create(WorkspaceModel.builder().name("One too many").build()))
                .isInstanceOf(WorkspaceLimitReachedException.class)
                .hasMessage(WorkspaceLimitReachedException.MESSAGE_KEY);
        verify(repository, never()).saveWithNewUniqueId(any(), any());
    }

    // -------------------------------------------------------------------------------- ensureDefault

    @Test
    void ensureDefault_existingDefault_isReturnedWithoutCreating() {
        when(repository.findByDefaultForOwner(OWNER))
                .thenReturn(Optional.of(defaultEntity()));

        var workspace = service.ensureDefault();

        assertThat(workspace.isDefaultWorkspace()).isTrue();
        verify(repository, never()).saveWithNewUniqueId(any(), any());
    }

    @Test
    void ensureDefault_absent_provisionsWithNoStoredName() {
        // A stored name cannot follow the viewer's locale, so the system-created default stores none and
        // the UI renders a localized label instead.
        when(repository.findByDefaultForOwner(OWNER)).thenReturn(Optional.empty());

        var workspace = service.ensureDefault();

        assertThat(workspace.getName()).isNull();
        assertThat(workspace.isSystemNamed()).isTrue();
        assertThat(workspace.isDefaultWorkspace()).isTrue();
    }

    @Test
    void ensureDefault_provisioningRace_recoversByReadingTheWinnersRow() {
        // The unique index on the default marker means one of two concurrent sessions must lose. The
        // loser must return the winner's workspace, not surface a constraint violation.
        when(repository.findByDefaultForOwner(OWNER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(defaultEntity()));
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate default"));

        var workspace = service.ensureDefault();

        assertThat(workspace.isDefaultWorkspace()).isTrue();
    }

    @Test
    void ensureDefault_raceWithNoRecoverableRow_rethrows() {
        // If the insert failed for some reason other than losing the race, hiding it would be wrong.
        when(repository.findByDefaultForOwner(OWNER)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> service.ensureDefault())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------------------ listOwned / find

    @Test
    void listOwned_filtersByOwnerRatherThanCheckingPerRow() {
        when(repository.findByOwnerUniqueIdOrderByCreatedAtAsc(OWNER))
                .thenReturn(java.util.List.of(defaultEntity()));

        assertThat(service.listOwned()).hasSize(1);
        verify(repository).findByOwnerUniqueIdOrderByCreatedAtAsc(OWNER);
    }

    @Test
    void find_unknownWorkspace_isEmpty() {
        var id = new UniqueId(999L);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.find(id)).isEmpty();
    }

    @Test
    void noAuthenticatedUser_isRejected() {
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listOwned()).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------------------------- update

    @Test
    void update_storesTheNewNameAndDescription() {
        var stored = named(77L, "Before");
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var updated = service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("  After  ").description(" Notes ").version(0).build());

        assertThat(updated.getName()).isEqualTo("After");
        assertThat(updated.getDescription()).isEqualTo("Notes");
    }

    @Test
    void update_namesASystemNamedWorkspace() {
        // The one-way transition: a system-created workspace stores no name until the user gives it one.
        var stored = defaultEntity();
        assertThat(stored.isSystemNamed()).isTrue();
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var updated = service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("Named at last").version(0).build());

        assertThat(updated.getName()).isEqualTo("Named at last");
        assertThat(updated.isSystemNamed()).isFalse();
    }

    @Test
    void update_cannotTransferOwnershipOrChangeTheDefaultMarker() {
        var stored = defaultEntity();
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("Renamed").version(0).build());

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                OWNER.equals(entity.getOwnerUniqueId()) && OWNER.equals(entity.getDefaultForOwner())));
    }

    @Test
    void update_blankName_isRejected() {
        assertThatThrownBy(() -> service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("  ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceServiceImpl.WORKSPACE_NAME_REQUIRED);
    }

    @Test
    void update_staleVersion_isRejected() {
        var stored = named(77L, "Before");
        stored.setVersion(3);
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("After").version(1).build()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_unknownWorkspace_isRejected() {
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(WorkspaceModel.builder()
                .uniqueId(new UniqueId(77L)).name("After").build()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ------------------------------------------------------------------------------------- delete

    @Test
    void delete_defaultWorkspace_isRefused() {
        when(repository.findById(new UniqueId(77L))).thenReturn(Optional.of(defaultEntity()));

        assertThatThrownBy(() -> service.delete(new UniqueId(77L)))
                .isInstanceOf(WorkspaceNotRemovableException.class)
                .hasMessage(WorkspaceNotRemovableException.MESSAGE_KEY);
        verify(repository, never()).delete(any());
        verify(contributor, never()).deleteAllInWorkspace(any());
    }

    @Test
    void delete_runsEveryContributorThenRemovesTheWorkspace() {
        var target = named(78L, "Doomed");
        when(repository.findById(new UniqueId(78L))).thenReturn(Optional.of(target));
        when(selectionRepository.findById(OWNER.getLongValue())).thenReturn(Optional.empty());

        service.delete(new UniqueId(78L));

        var order = org.mockito.Mockito.inOrder(contributor, repository);
        order.verify(contributor).deleteAllInWorkspace(new UniqueId(78L));
        order.verify(repository).delete(target);
    }

    @Test
    void delete_repointsTheSelectionBeforeRemovingTheWorkspace() {
        // The database enforces this order: rg_workspace_selection has a foreign key to rg_workspace, so
        // deleting a selected workspace without repointing first fails outright.
        var target = named(78L, "Doomed");
        when(repository.findById(new UniqueId(78L))).thenReturn(Optional.of(target));
        when(selectionRepository.findById(OWNER.getLongValue()))
                .thenReturn(Optional.of(WorkspaceSelectionEntity.builder()
                        .userUniqueId(OWNER.getLongValue())
                        .workspaceUniqueId(new UniqueId(78L))
                        .build()));
        when(repository.findByDefaultForOwner(OWNER)).thenReturn(Optional.of(defaultEntity()));

        service.delete(new UniqueId(78L));

        var order = org.mockito.Mockito.inOrder(selectionRepository, repository);
        order.verify(selectionRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(row ->
                new UniqueId(77L).equals(row.getWorkspaceUniqueId())));
        order.verify(repository).delete(target);
    }

    @Test
    void delete_selectionPointingElsewhere_isLeftAlone() {
        var target = named(78L, "Doomed");
        when(repository.findById(new UniqueId(78L))).thenReturn(Optional.of(target));
        when(selectionRepository.findById(OWNER.getLongValue()))
                .thenReturn(Optional.of(WorkspaceSelectionEntity.builder()
                        .userUniqueId(OWNER.getLongValue())
                        .workspaceUniqueId(new UniqueId(99L))
                        .build()));

        service.delete(new UniqueId(78L));

        verify(selectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_unknownWorkspace_isRejected() {
        when(repository.findById(new UniqueId(78L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new UniqueId(78L)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static WorkspaceEntity named(long id, String name) {
        return WorkspaceEntity.builder()
                .uniqueId(id)
                .ownerUniqueId(OWNER)
                .name(name)
                .build();
    }

    private static WorkspaceEntity defaultEntity() {
        return WorkspaceEntity.builder()
                .uniqueId(77L)
                .ownerUniqueId(OWNER)
                .defaultForOwner(OWNER)
                .build();
    }
}
