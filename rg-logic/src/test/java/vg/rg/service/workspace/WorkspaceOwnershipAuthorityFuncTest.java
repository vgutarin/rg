package vg.rg.service.workspace;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import vg.rg.model.geo.ProximityQuery;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
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
 * The two halves of the access rule, end to end against MySQL.
 *
 * <p><b>Ownership is sufficient.</b> A workspace owner holding <em>none</em> of the local capabilities
 * succeeds on every operation inside their own workspace — that is the ownership override, and it is why
 * no UI gate may add a capability check on top of the section.
 *
 * <p><b>Ownership is necessary.</b> A user who does not own the workspace is denied every operation, on
 * the workspace and on everything inside it, whichever identifier they address.
 *
 * <p>Method security is enabled explicitly; {@code BaseFuncTest} does not enable it, so without this the
 * guards would be inert and every denial assertion would pass vacuously.
 */
class WorkspaceOwnershipAuthorityFuncTest extends BaseFuncTest {


    private static final UniqueId OWNER = new UniqueId(3401L);
    private static final UniqueId STRANGER = new UniqueId(3402L);

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
        // The owner holds ONLY the app-wide gate. No location:* or workspace:* capability anywhere in
        // this test, which is the point.
        authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));
        workspace = workspaceService.create(WorkspaceModel.builder().name("Private").build())
                .getUniqueId();
        location = locationService.create(workspace, LocationModel.builder()
                .name("Depot")
                .latitude(BigDecimal.valueOf(50.0))
                .longitude(BigDecimal.valueOf(30.0))
                .build());
    }

    @AfterEach
    void cleanUp() {
        selectionRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------- ownership is sufficient (SC-006)

    @Test
    void owner_holdingNoLocalCapability_succeedsOnEveryLocationOperation() {
        assertThat(currentPermissions()).containsExactly(Permissions.Workspace.OWNER);

        assertThatCode(() -> locationService.browse(workspace, PageRequest.of(0, 50)))
                .doesNotThrowAnyException();
        assertThatCode(() -> locationService.searchByName(workspace, "Depot", 50))
                .doesNotThrowAnyException();
        assertThatCode(() -> locationService.findNearby(workspace, new ProximityQuery(
                BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> locationService.create(workspace, LocationModel.builder()
                .name("Another").build())).doesNotThrowAnyException();

        location.setName("Renamed");
        assertThatCode(() -> locationService.update(location)).doesNotThrowAnyException();
        assertThatCode(() -> locationService.delete(location.getUniqueId()))
                .doesNotThrowAnyException();
    }

    @Test
    void owner_holdingNoLocalCapability_succeedsOnEveryWorkspaceOperation() {
        assertThatCode(() -> workspaceService.find(workspace)).doesNotThrowAnyException();
        assertThatCode(workspaceService::listOwned).doesNotThrowAnyException();
        assertThatCode(workspaceService::ensureDefault).doesNotThrowAnyException();
        assertThatCode(() -> selectionService.select(workspace)).doesNotThrowAnyException();
        assertThatCode(selectionService::activeWorkspace).doesNotThrowAnyException();
    }

    @Test
    void holdingEveryLocalCapabilityButNotTheGate_isDeniedEverything() {
        // The mirror image: local capabilities are not the gate. Without workspace:owner the layer is
        // closed, however many capabilities are granted.
        authenticate(OWNER, LocalPermissions.ALL);

        assertDenied(() -> locationService.browse(workspace, PageRequest.of(0, 50)));
        assertDenied(() -> locationService.create(workspace, LocationModel.builder()
                .name("Nope").build()));
        assertDenied(() -> locationService.delete(location.getUniqueId()));
        assertDenied(() -> workspaceService.find(workspace));
        assertDenied(workspaceService::listOwned);
        assertDenied(() -> selectionService.select(workspace));
    }

    // ---------------------------------------------------------- ownership is necessary (SC-005)

    @Test
    void nonOwner_isDeniedEveryOperationOnTheWorkspace() {
        authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));

        assertDenied(() -> workspaceService.find(workspace));
        assertDenied(() -> selectionService.select(workspace));
    }

    @Test
    void nonOwner_isDeniedEveryOperationOnTheWorkspacesContents() {
        authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));

        assertDenied(() -> locationService.browse(workspace, PageRequest.of(0, 50)));
        assertDenied(() -> locationService.searchByName(workspace, "Depot", 50));
        assertDenied(() -> locationService.findNearby(workspace, new ProximityQuery(
                BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null)));
        assertDenied(() -> locationService.create(workspace, LocationModel.builder()
                .name("Intruder").build()));
        // Addressed by the location's own identifier, so these exercise the join path too.
        assertDenied(() -> locationService.update(location));
        assertDenied(() -> locationService.delete(location.getUniqueId()));
    }

    @Test
    void nonOwner_holdingEveryLocalCapabilityToo_isStillDenied() {
        var everything = new java.util.HashSet<String>(LocalPermissions.ALL);
        everything.add(Permissions.Workspace.OWNER);
        authenticate(STRANGER, everything);

        assertDenied(() -> locationService.browse(workspace, PageRequest.of(0, 50)));
        assertDenied(() -> locationService.delete(location.getUniqueId()));
        assertDenied(() -> workspaceService.find(workspace));
    }

    @Test
    void nonOwner_seesNothingOfTheWorkspaceInTheirOwnListing() {
        // Denial is not the only requirement: another user's workspace must not appear at all.
        authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));

        assertThat(workspaceService.listOwned())
                .extracting(WorkspaceModel::getUniqueId)
                .doesNotContain(workspace);
    }

    @Test
    void denials_doNotDistinguishAbsentFromForbidden() {
        // A denial must not become an oracle for existence: acting on someone else's workspace and on an
        // identifier that never existed fail the same way.
        authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));
        var neverExisted = new UniqueId(424242L);

        var forbidden = denialOf(() -> locationService.browse(workspace, PageRequest.of(0, 50)));
        var absent = denialOf(() -> locationService.browse(neverExisted, PageRequest.of(0, 50)));

        assertThat(forbidden).isEqualTo(absent);
    }

    private static void assertDenied(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(AccessDeniedException.class);
    }

    /** The denial's type and message, so two denials can be compared for indistinguishability. */
    private static String denialOf(ThrowingCallable operation) {
        try {
            operation.call();
            throw new AssertionError("expected the operation to be denied");
        } catch (Throwable denial) {
            return denial.getClass().getName() + ": " + denial.getMessage();
        }
    }

    private static Set<String> currentPermissions() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((AuthenticatedUserPrincipal) authentication.getPrincipal()).permissions();
    }

    private static void authenticate(UniqueId user, Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                user, "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
