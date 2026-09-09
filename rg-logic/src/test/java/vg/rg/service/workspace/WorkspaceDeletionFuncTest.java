package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.exception.workspace.WorkspaceLimitReachedException;
import vg.rg.exception.workspace.WorkspaceNotRemovableException;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Workspace removal and rename against MySQL.
 *
 * <p>Removal is where the foreign key from {@code rg_workspace_selection} bites: the selection must be
 * repointed before the workspace row goes, and the database — not the service's good intentions — is
 * what enforces that. These tests exercise the real constraint, which a mocked repository cannot.
 */
class WorkspaceDeletionFuncTest extends BaseFuncTest {


    private static final UniqueId OWNER = new UniqueId(3601L);

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private WorkspaceLocationService locationService;
    @Autowired
    private WorkspaceSelectionService selectionService;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceLocationRepository locationRepository;
    @Autowired
    private WorkspaceSelectionRepository selectionRepository;

    private UniqueId doomed;
    private UniqueId survivor;

    @BeforeEach
    void setUp() {
        authenticate();
        doomed = workspaceService.create(WorkspaceModel.builder().name("Doomed").build()).getUniqueId();
        survivor = workspaceService.create(WorkspaceModel.builder().name("Survivor").build())
                .getUniqueId();
        locationService.create(doomed, location("In doomed"));
        locationService.create(doomed, location("Also in doomed"));
        locationService.create(survivor, location("In survivor"));
    }

    @AfterEach
    void cleanUp() {
        selectionRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void delete_removesTheWorkspaceAndAllItsContents() {
        workspaceService.delete(doomed);

        assertThat(workspaceRepository.findById(doomed)).isEmpty();
        assertThat(locationRepository.countByWorkspaceUniqueId(doomed)).isZero();
    }

    @Test
    void delete_leavesEveryOtherWorkspaceUntouched() {
        workspaceService.delete(doomed);

        assertThat(workspaceRepository.findById(survivor)).isPresent();
        assertThat(locationRepository.countByWorkspaceUniqueId(survivor)).isEqualTo(1);
        assertThat(locationService.browse(survivor, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getName)
                .containsExactly("In survivor");
    }

    @Test
    void delete_whileSelected_succeedsAndRepointsTheSelection() {
        // The case the foreign key would otherwise reject. Repointing has to happen first, and the only
        // way to prove the ordering is against the real constraint.
        selectionService.select(doomed);

        assertThatCode(() -> workspaceService.delete(doomed)).doesNotThrowAnyException();

        var active = selectionService.activeWorkspace();
        assertThat(active.getUniqueId()).isNotEqualTo(doomed);
        assertThat(active.isDefaultWorkspace()).isTrue();
    }

    @Test
    void delete_whileSelected_leavesExactlyOneSelectionRow() {
        selectionService.select(doomed);

        workspaceService.delete(doomed);

        assertThat(selectionRepository.count()).isEqualTo(1);
        assertThat(selectionRepository.findById(OWNER.getLongValue()))
                .get()
                .extracting(row -> row.getWorkspaceUniqueId())
                .isNotEqualTo(doomed);
    }

    @Test
    void delete_whileAnotherWorkspaceIsSelected_leavesTheSelectionAlone() {
        selectionService.select(survivor);

        workspaceService.delete(doomed);

        assertThat(selectionService.activeWorkspace().getUniqueId()).isEqualTo(survivor);
    }

    @Test
    void delete_theDefaultWorkspace_isRefused() {
        var defaultWorkspace = selectionService.activeWorkspace();
        assertThat(defaultWorkspace.isDefaultWorkspace()).isTrue();

        assertThatThrownBy(() -> workspaceService.delete(defaultWorkspace.getUniqueId()))
                .isInstanceOf(WorkspaceNotRemovableException.class);
        assertThat(workspaceRepository.findById(defaultWorkspace.getUniqueId())).isPresent();
    }

    @Test
    void afterEveryRemoval_theUserStillHasAnAccessibleWorkspace() {
        // The guarantee behind refusing the default: however many workspaces are removed, the user is
        // never left with none.
        workspaceService.delete(doomed);
        workspaceService.delete(survivor);

        assertThat(workspaceService.listOwned()).isNotEmpty();
        assertThat(selectionService.activeWorkspace()).isNotNull();
    }

    @Test
    void rename_storesTheNewNameAndKeepsContents() {
        var loaded = workspaceService.find(doomed).orElseThrow();
        loaded.setName("Renamed");

        var renamed = workspaceService.update(loaded);

        assertThat(renamed.getName()).isEqualTo("Renamed");
        assertThat(locationRepository.countByWorkspaceUniqueId(doomed)).isEqualTo(2);
    }

    @Test
    void rename_aSystemNamedWorkspace_givesItAStoredName() {
        var systemNamed = selectionService.activeWorkspace();
        assertThat(systemNamed.isSystemNamed()).isTrue();
        systemNamed.setName("My places");

        var renamed = workspaceService.update(systemNamed);

        assertThat(renamed.isSystemNamed()).isFalse();
        assertThat(renamed.getName()).isEqualTo("My places");
        // It remains the default: renaming must not change which workspace cannot be removed.
        assertThat(renamed.isDefaultWorkspace()).isTrue();
        assertThatThrownBy(() -> workspaceService.delete(renamed.getUniqueId()))
                .isInstanceOf(WorkspaceNotRemovableException.class);
    }

    @Test
    void rename_staleVersion_isRejectedWithoutOverwriting() {
        var first = workspaceService.find(doomed).orElseThrow();
        var second = workspaceService.find(doomed).orElseThrow();

        first.setName("Winner");
        workspaceService.update(first);

        second.setName("Loser");
        assertThatThrownBy(() -> workspaceService.update(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(workspaceService.find(doomed).orElseThrow().getName()).isEqualTo("Winner");
    }

    @Test
    void workspaceLimit_isRefusedAtTheBound() {
        // The default plus the two from setUp already exist; fill to the configured bound of 20 and then
        // ask for one more.
        while (workspaceService.listOwned().size() < 20) {
            workspaceService.create(WorkspaceModel.builder()
                    .name("Filler " + workspaceService.listOwned().size()).build());
        }

        assertThatThrownBy(() -> workspaceService.create(
                WorkspaceModel.builder().name("One too many").build()))
                .isInstanceOf(WorkspaceLimitReachedException.class);
        // The existing ones stay usable.
        assertThat(workspaceService.listOwned()).hasSize(20);
    }

    private static LocationModel location(String name) {
        return LocationModel.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(50.0))
                .longitude(BigDecimal.valueOf(30.0))
                .build();
    }

    private static void authenticate() {
        var principal = new AuthenticatedUserPrincipal(
                OWNER, "Test User", Set.of(Permissions.Workspace.OWNER), true,
                AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
