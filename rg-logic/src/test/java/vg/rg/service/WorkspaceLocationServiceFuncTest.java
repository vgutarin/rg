package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.WorkspaceLocationRepository;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-backed coverage of {@link WorkspaceLocationService} against MySQL, end to end through the real
 * authority boundary and the real workspace-scope join.
 *
 * <p>The central assertion is isolation: a workspace read must never return another workspace's rows,
 * and a location must not be reachable while a different workspace is in context.
 *
 * <p>Method security needs no setup here: {@code @EnableMethodSecurity} lives in {@code RgLogicConfig},
 * the module that declares the guards, so it is active in every context scanning {@code vg.rg} —
 * {@code BaseFuncTest} included. That placement is what keeps the denial assertions below meaningful:
 * a guard that never activates is not a weaker guard but no guard, and every such assertion would pass
 * vacuously. See {@code specs/current/engineering-notes.md}.
 */
class WorkspaceLocationServiceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(3101L);
    private static final UniqueId STRANGER = new UniqueId(3102L);
    private static final double BASE_LAT = 50.0;
    private static final double BASE_LNG = 30.0;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceLocationService service;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceLocationRepository locationRepository;

    private UniqueId workspaceA;
    private UniqueId workspaceB;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        // The owner holds only the layer gate: no location:* capability anywhere in this test, which
        // proves ownership alone is sufficient inside one's own workspace.
        workspaceA = workspaceService.create(
                vg.rg.model.WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        workspaceB = workspaceService.create(
                vg.rg.model.WorkspaceModel.builder().name("Beta").build()).getUniqueId();
    }

    @AfterEach
    void cleanUp() {
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createdLocation_isListedInItsWorkspaceOnly() {
        var created = service.create(workspaceA, location("Depot", BASE_LAT, BASE_LNG));

        assertThat(created.getUniqueId()).isNotNull();
        assertThat(service.browse(workspaceA, PageRequest.of(0, 10)).getContent())
                .extracting(LocationModel::getName)
                .containsExactly("Depot");
        assertThat(service.browse(workspaceB, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void nameSearch_neverReturnsAnotherWorkspacesRows() {
        service.create(workspaceA, location("Alpha depot", BASE_LAT, BASE_LNG));
        service.create(workspaceB, location("Beta depot", BASE_LAT, BASE_LNG));

        assertThat(service.searchByName(workspaceA, "depot", 10))
                .extracting(LocationModel::getName)
                .containsExactly("Alpha depot");
        assertThat(service.searchByName(workspaceB, "depot", 10))
                .extracting(LocationModel::getName)
                .containsExactly("Beta depot");
        // A name that exists only in the other workspace must not be found here.
        assertThat(service.searchByName(workspaceA, "Beta", 10)).isEmpty();
    }

    @Test
    void proximitySuggestion_neverCrossesAWorkspaceBoundary() {
        // Identical coordinates in both workspaces -- exactly what the migration will produce.
        service.create(workspaceA, location("Alpha point", BASE_LAT, BASE_LNG));
        service.create(workspaceB, location("Beta point", BASE_LAT, BASE_LNG));

        var matches = service.findNearby(workspaceA, new ProximityQuery(
                BigDecimal.valueOf(BASE_LAT), BigDecimal.valueOf(BASE_LNG), null));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).location().getName()).isEqualTo("Alpha point");
    }

    @Test
    void update_resolvesFromTheLocationsOwnIdentifier() {
        // The call site passes the location id, not its workspace: the resolver finds the workspace
        // through the join.
        var created = service.create(workspaceA, location("Before", BASE_LAT, BASE_LNG));
        created.setName("After");

        var updated = service.update(created);

        assertThat(updated.getName()).isEqualTo("After");
        assertThat(service.browse(workspaceA, PageRequest.of(0, 10)).getContent())
                .extracting(LocationModel::getName)
                .containsExactly("After");
    }

    @Test
    void delete_removesOnlyThatLocation() {
        var kept = service.create(workspaceA, location("Kept", BASE_LAT, BASE_LNG));
        var removed = service.create(workspaceA, location("Removed", BASE_LAT, BASE_LNG));

        service.delete(removed.getUniqueId());

        assertThat(service.browse(workspaceA, PageRequest.of(0, 10)).getContent())
                .extracting(LocationModel::getUniqueId)
                .containsExactly(kept.getUniqueId());
    }

    @Test
    void locationWithoutCoordinates_isAccepted() {
        var created = service.create(
                workspaceA, LocationModel.builder().name("No coordinates").build());

        assertThat(created.getLatitude()).isNull();
        assertThat(service.browse(workspaceA, PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void anotherUsersWorkspace_isDeniedEveryOperation() {
        var foreignLocation = service.create(workspaceA, location("Private", BASE_LAT, BASE_LNG));

        authenticate(STRANGER);

        assertThatThrownBy(() -> service.browse(workspaceA, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.searchByName(workspaceA, "Private", 10))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(workspaceA, location("Intruder", BASE_LAT, BASE_LNG)))
                .isInstanceOf(AccessDeniedException.class);
        // Addressed by the location's own identifier, so this exercises the join path too.
        assertThatThrownBy(() -> service.delete(foreignLocation.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownWorkspaceIdentifier_isDenied() {
        assertThatThrownBy(() ->
                service.browse(new UniqueId(424242L), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static LocationModel location(String name, double latitude, double longitude) {
        return LocationModel.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .build();
    }

    private static void authenticate(UniqueId user) {
        var principal = new AuthenticatedUserPrincipal(
                user, "Test User",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
