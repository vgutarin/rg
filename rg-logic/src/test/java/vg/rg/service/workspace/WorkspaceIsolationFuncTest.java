package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vg.rg.BaseFuncTest;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.geo.ProximityQuery;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

/**
 * The zero-leakage guarantee, against MySQL: across every read path, a workspace returns only its own
 * rows, and a resource belonging to one workspace is unreachable while another is in context.
 *
 * <p>Method security is enabled explicitly. {@code BaseFuncTest} does not enable it — the application's
 * {@code @EnableMethodSecurity} lives in the UI module — so without this the {@code @PreAuthorize} guards
 * would be inert and the "is denied" assertions would pass vacuously.
 */
class WorkspaceIsolationFuncTest extends BaseFuncTest {


    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId STRANGER = nextUniqueId();
    private static final double LAT = 50.0;
    private static final double LNG = 30.0;

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private WorkspaceLocationService locationService;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceLocationRepository locationRepository;
    @Autowired
    private WorkspaceSelectionRepository selectionRepository;

    private UniqueId alpha;
    private UniqueId beta;
    private LocationModel inAlpha;
    private LocationModel inBeta;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        alpha = workspaceService.create(WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        beta = workspaceService.create(WorkspaceModel.builder().name("Beta").build()).getUniqueId();
        // Same name and same coordinates in both -- exactly what the migration will produce, and the
        // hardest case for isolation to get right.
        inAlpha = locationService.create(alpha, location("Shared depot"));
        inBeta = locationService.create(beta, location("Shared depot"));
    }

    @AfterEach
    void cleanUp() {
        selectionRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void browse_returnsOnlyTheWorkspacesOwnRows() {
        assertThat(locationService.browse(alpha, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getUniqueId)
                .containsExactly(inAlpha.getUniqueId());
        assertThat(locationService.browse(beta, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getUniqueId)
                .containsExactly(inBeta.getUniqueId());
    }

    @Test
    void search_returnsOnlyTheWorkspacesOwnRows_evenForAnIdenticalName() {
        assertThat(locationService.searchByName(alpha, "Shared depot", 50))
                .extracting(LocationModel::getUniqueId)
                .containsExactly(inAlpha.getUniqueId());
        assertThat(locationService.searchByName(beta, "Shared depot", 50))
                .extracting(LocationModel::getUniqueId)
                .containsExactly(inBeta.getUniqueId());
    }

    @Test
    void proximity_returnsOnlyTheWorkspacesOwnRows_atIdenticalCoordinates() {
        var query = new ProximityQuery(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LNG), null);

        assertThat(locationService.findNearby(alpha, query))
                .extracting(match -> match.location().getUniqueId())
                .containsExactly(inAlpha.getUniqueId());
        assertThat(locationService.findNearby(beta, query))
                .extracting(match -> match.location().getUniqueId())
                .containsExactly(inBeta.getUniqueId());
    }

    @Test
    void count_isPerWorkspace() {
        assertThat(locationRepository.countByWorkspaceUniqueId(alpha)).isEqualTo(1);
        assertThat(locationRepository.countByWorkspaceUniqueId(beta)).isEqualTo(1);
    }

    @Test
    void everyReadPath_leaksNothing() {
        // The aggregate assertion behind the zero-leakage criterion: no read path returns a row from
        // another workspace, whichever workspace is in context.
        var query = new ProximityQuery(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LNG), null);
        List<UniqueId> fromAlpha = List.of();
        fromAlpha = concat(
                ids(locationService.browse(alpha, PageRequest.of(0, 50)).getContent()),
                ids(locationService.searchByName(alpha, "", 50)),
                locationService.findNearby(alpha, query).stream()
                        .map(match -> match.location().getUniqueId()).toList());

        assertThat(fromAlpha).isNotEmpty();
        assertThat(fromAlpha).doesNotContain(inBeta.getUniqueId());
        assertThat(fromAlpha).allMatch(id -> id.equals(inAlpha.getUniqueId()));
    }

    @Test
    void aLocationOfAnotherWorkspace_isStillReachableByItsOwner() {
        // Isolation is about queries, not about ownership: the same owner owns both workspaces, so
        // addressing a location by its own identifier works regardless of which workspace is "active".
        inBeta.setName("Renamed from elsewhere");

        var updated = locationService.update(inBeta);

        assertThat(updated.getName()).isEqualTo("Renamed from elsewhere");
        // ...and it stayed in Beta.
        assertThat(locationService.browse(beta, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getName)
                .containsExactly("Renamed from elsewhere");
        assertThat(locationService.browse(alpha, PageRequest.of(0, 50)).getContent())
                .extracting(LocationModel::getName)
                .containsExactly("Shared depot");
    }

    @Test
    void anotherUsersWorkspaceAndItsContents_areUnreachable() {
        authenticate(STRANGER);

        assertThatThrownBy(() -> locationService.browse(alpha, PageRequest.of(0, 50)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> locationService.searchByName(alpha, "Shared", 50))
                .isInstanceOf(AccessDeniedException.class);
        // Addressed by the location's own identifier, so this exercises the join path as well.
        assertThatThrownBy(() -> locationService.delete(inAlpha.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workspaceService.find(alpha))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletingAWorkspacesLocation_leavesTheOtherWorkspaceUntouched() {
        locationService.delete(inAlpha.getUniqueId());

        assertThat(locationService.browse(alpha, PageRequest.of(0, 50)).getContent()).isEmpty();
        assertThat(locationService.browse(beta, PageRequest.of(0, 50)).getContent()).hasSize(1);
    }

    private static List<UniqueId> ids(List<LocationModel> models) {
        return models.stream().map(LocationModel::getUniqueId).toList();
    }

    @SafeVarargs
    private static List<UniqueId> concat(List<UniqueId>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
    }

    private static LocationModel location(String name) {
        return LocationModel.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(LAT))
                .longitude(BigDecimal.valueOf(LNG))
                .build();
    }

}
