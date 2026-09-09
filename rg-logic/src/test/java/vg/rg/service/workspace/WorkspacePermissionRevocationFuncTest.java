package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Revoking the workspace permission withdraws <em>access</em>, not data.
 *
 * <p>This is the property that makes the gate safe to toggle: nothing about a permission change may
 * delete a user's work. Silent data loss on a configuration change would be unrecoverable, so the
 * counts are asserted directly against the database rather than through the services that are, by then,
 * refusing to answer.
 */
class WorkspacePermissionRevocationFuncTest extends BaseFuncTest {


    private static final UniqueId USER = new UniqueId(3501L);

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

    private UniqueId workspace;
    private LocationModel location;

    @BeforeEach
    void setUp() {
        granted();
        workspace = workspaceService.create(WorkspaceModel.builder().name("Mine").build())
                .getUniqueId();
        location = locationService.create(workspace, LocationModel.builder()
                .name("Depot")
                .latitude(BigDecimal.valueOf(50.0))
                .longitude(BigDecimal.valueOf(30.0))
                .build());
        selectionService.select(workspace);
    }

    @AfterEach
    void cleanUp() {
        selectionRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void revoked_deniesEveryOperation() {
        revoked();

        assertThatThrownBy(() -> workspaceService.listOwned())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workspaceService.find(workspace))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workspaceService.ensureDefault())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> selectionService.activeWorkspace())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> locationService.browse(workspace, PageRequest.of(0, 50)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> locationService.delete(location.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revoked_deletesNothing() {
        // Asserted against the repositories, because the services are refusing to answer by now.
        revoked();

        assertThat(workspaceRepository.count()).isEqualTo(1);
        assertThat(locationRepository.countByWorkspaceUniqueId(workspace)).isEqualTo(1);
        assertThat(selectionRepository.count()).isEqualTo(1);
    }

    @Test
    void revoked_provisionsNothingEither() {
        // A denied caller must not leave a workspace behind as a side effect of being denied.
        var before = workspaceRepository.count();
        revoked();

        assertThatThrownBy(() -> selectionService.activeWorkspace())
                .isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.count()).isEqualTo(before);
    }

    @Test
    void reGranted_restoresAccessToTheSameContent() {
        revoked();
        granted();

        assertThat(workspaceService.listOwned())
                .extracting(WorkspaceModel::getUniqueId)
                .containsExactly(workspace);
        assertThat(locationService.browse(workspace, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getUniqueId)
                .containsExactly(location.getUniqueId());
    }

    @Test
    void reGranted_restoresTheSameActiveWorkspace() {
        // The selection is data too: it must survive the revocation rather than resetting to the default.
        revoked();
        granted();

        assertThat(selectionService.activeWorkspace().getUniqueId()).isEqualTo(workspace);
    }

    @Test
    void revocationIsReversible_endToEnd() {
        // The whole cycle in one assertion: usable, closed, usable again with the same content.
        assertThat(locationService.browse(workspace, PageRequest.of(0, 50)).getContent()).hasSize(1);

        revoked();
        assertThatThrownBy(() -> locationService.browse(workspace, PageRequest.of(0, 50)))
                .isInstanceOf(AccessDeniedException.class);

        granted();
        assertThat(locationService.browse(workspace, PageRequest.of(0, 50)).getContent()).hasSize(1);
    }

    private static void granted() {
        authenticate(Set.of(Permissions.Workspace.OWNER));
    }

    /** The same user, still owning the same rows, but without the gate. */
    private static void revoked() {
        authenticate(Set.of(Permissions.Reports.READ));
    }

    private static void authenticate(Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                USER, "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
