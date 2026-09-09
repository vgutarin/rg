package vg.rg.service.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;
import vg.rg.mapper.workspace.WorkspaceMapper;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The active-workspace pointer. The behaviour that matters here is that a <em>stale</em> pointer never
 * surfaces as an error: this is the single entry point the workspace section uses, so it repairs itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceSelectionServiceImplTest {

    private static final UniqueId USER = new UniqueId(4101L);
    private static final UniqueId OTHER_USER = new UniqueId(4102L);
    private static final UniqueId SELECTED = new UniqueId(5101L);
    private static final UniqueId DEFAULT = new UniqueId(5102L);

    @Mock
    private WorkspaceSelectionRepository selectionRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private WorkspaceMapper mapper;
    @Mock
    private AuthorityChecker authorityChecker;

    private WorkspaceSelectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceSelectionServiceImpl(
                selectionRepository, workspaceRepository, workspaceService, mapper, authorityChecker);
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(USER));
        when(mapper.toModel(any())).thenAnswer(invocation -> {
            WorkspaceEntity entity = invocation.getArgument(0);
            return WorkspaceModel.builder().uniqueId(new UniqueId(entity.getUniqueId())).build();
        });
        when(workspaceService.ensureDefault())
                .thenReturn(WorkspaceModel.builder().uniqueId(DEFAULT).build());
    }

    // -------------------------------------------------------------------------------- activeWorkspace

    @Test
    void activeWorkspace_validSelection_isReturned() {
        selectionPointsTo(SELECTED);
        when(workspaceRepository.findById(SELECTED)).thenReturn(Optional.of(owned(SELECTED, USER)));

        assertThat(service.activeWorkspace().getUniqueId()).isEqualTo(SELECTED);
        verify(workspaceService, never()).ensureDefault();
    }

    @Test
    void activeWorkspace_noSelection_fallsBackToTheDefaultAndStoresIt() {
        when(selectionRepository.findById(USER.getLongValue())).thenReturn(Optional.empty());

        assertThat(service.activeWorkspace().getUniqueId()).isEqualTo(DEFAULT);
        assertThat(storedSelection().getWorkspaceUniqueId()).isEqualTo(DEFAULT);
    }

    @Test
    void activeWorkspace_danglingSelection_isRepaired() {
        // The selected workspace was removed. Falling back and repairing the row is what keeps a stale
        // pointer from surfacing as an error screen.
        selectionPointsTo(SELECTED);
        when(workspaceRepository.findById(SELECTED)).thenReturn(Optional.empty());

        assertThat(service.activeWorkspace().getUniqueId()).isEqualTo(DEFAULT);
        assertThat(storedSelection().getWorkspaceUniqueId()).isEqualTo(DEFAULT);
    }

    @Test
    void activeWorkspace_selectionNoLongerOwned_isRepaired() {
        // Ownership is re-checked on every read rather than trusted from when it was stored.
        selectionPointsTo(SELECTED);
        when(workspaceRepository.findById(SELECTED))
                .thenReturn(Optional.of(owned(SELECTED, OTHER_USER)));

        assertThat(service.activeWorkspace().getUniqueId()).isEqualTo(DEFAULT);
        assertThat(storedSelection().getWorkspaceUniqueId()).isEqualTo(DEFAULT);
    }

    @Test
    void activeWorkspace_validSelection_doesNotRewriteTheRow() {
        selectionPointsTo(SELECTED);
        when(workspaceRepository.findById(SELECTED)).thenReturn(Optional.of(owned(SELECTED, USER)));

        service.activeWorkspace();

        verify(selectionRepository, never()).save(any());
    }

    // ------------------------------------------------------------------------------------- select

    @Test
    void select_storesOneRowKeyedByTheUser() {
        // One row per user is the "exactly one active workspace" rule, so a switch overwrites rather
        // than accumulating.
        service.select(SELECTED);

        var stored = storedSelection();
        assertThat(stored.getUserUniqueId()).isEqualTo(USER.getLongValue());
        assertThat(stored.getWorkspaceUniqueId()).isEqualTo(SELECTED);
    }

    @Test
    void select_isLastWriteWins() {
        service.select(SELECTED);
        service.select(DEFAULT);

        var captor = ArgumentCaptor.forClass(WorkspaceSelectionEntity.class);
        verify(selectionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(row -> assertThat(row.getUserUniqueId()).isEqualTo(USER.getLongValue()));
        assertThat(captor.getValue().getWorkspaceUniqueId()).isEqualTo(DEFAULT);
    }

    @Test
    void select_nullWorkspace_isRejected() {
        assertThatThrownBy(() -> service.select(null)).isInstanceOf(NullPointerException.class);
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void noAuthenticatedUser_isRejected() {
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activeWorkspace()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.select(SELECTED)).isInstanceOf(IllegalStateException.class);
    }

    private void selectionPointsTo(UniqueId workspaceId) {
        when(selectionRepository.findById(USER.getLongValue()))
                .thenReturn(Optional.of(WorkspaceSelectionEntity.builder()
                        .userUniqueId(USER.getLongValue())
                        .workspaceUniqueId(workspaceId)
                        .build()));
    }

    private WorkspaceSelectionEntity storedSelection() {
        var captor = ArgumentCaptor.forClass(WorkspaceSelectionEntity.class);
        verify(selectionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static WorkspaceEntity owned(UniqueId workspaceId, UniqueId owner) {
        return WorkspaceEntity.builder()
                .uniqueId(workspaceId.getLongValue())
                .ownerUniqueId(owner)
                .build();
    }
}
