package vg.rg.service.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.config.GeoProperties;
import vg.rg.entity.workspace.WorkspaceLocationEntity;
import vg.rg.mapper.workspace.WorkspaceLocationMapper;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.geo.ProximityQuery;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceLocationServiceImplTest {

    // Deliberately not the production defaults (500/50): only non-default bounds prove the configured
    // values actually reach the service rather than being hard-coded in it.
    private static final int MATCH_RADIUS_METERS = 400;
    private static final int MAX_NAME_SEARCH_RESULTS = 7;
    private static final UniqueId WORKSPACE = nextUniqueId();
    private static final UniqueId OTHER_WORKSPACE = nextUniqueId();
    private static final UniqueId LOCATION = nextUniqueId();

    @Mock
    private UniqueIdService uniqueIdService;
    @Mock
    private WorkspaceLocationRepository repository;
    @Mock
    private WorkspaceLocationMapper mapper;

    private WorkspaceLocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceLocationServiceImpl(
                uniqueIdService, repository, mapper, new GeoProperties(
                        String.valueOf(MATCH_RADIUS_METERS), String.valueOf(MAX_NAME_SEARCH_RESULTS)));
        when(mapper.toEntity(any())).thenAnswer(invocation -> {
            LocationModel model = invocation.getArgument(0);
            return WorkspaceLocationEntity.builder()
                    .name(model.getName())
                    .description(model.getDescription())
                    .latitude(model.getLatitude())
                    .longitude(model.getLongitude())
                    .googlePlaceId(model.getGooglePlaceId())
                    .build();
        });
        when(mapper.toModel(any())).thenAnswer(invocation -> {
            WorkspaceLocationEntity entity = invocation.getArgument(0);
            return LocationModel.builder()
                    .name(entity.getName())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .build();
        });
        when(repository.saveWithNewUniqueId(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------------------------- create

    @Test
    void create_setsTheGivenWorkspaceAndNoOther() {
        // The scope comes from the operation, not the model -- which is what makes it unforgeable.
        service.create(WORKSPACE, LocationModel.builder().name("Depot").build());

        verify(repository).saveWithNewUniqueId(
                argThat(entity -> WORKSPACE.equals(entity.getWorkspaceUniqueId())), any());
    }

    @Test
    void create_ignoresAnyScopeImpliedByTheModel() {
        // LocationModel carries no workspace field at all, so this is really an assertion about the
        // mapper contract: nothing the caller supplies can redirect the scope.
        service.create(OTHER_WORKSPACE, LocationModel.builder().name("Depot").build());

        verify(repository).saveWithNewUniqueId(
                argThat(entity -> OTHER_WORKSPACE.equals(entity.getWorkspaceUniqueId())), any());
    }

    @Test
    void create_withoutCoordinates_isAllowed() {
        // Coordinates stay optional: a location may be saved when Google Maps is unavailable.
        var created = service.create(WORKSPACE, LocationModel.builder().name("No coords").build());

        assertThat(created.getLatitude()).isNull();
        assertThat(created.getLongitude()).isNull();
    }

    @Test
    void create_blankName_isRejected() {
        assertThatThrownBy(() -> service.create(WORKSPACE, LocationModel.builder().name(" ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name is required");
    }

    @Test
    void create_halfACoordinatePair_isRejected() {
        var model = LocationModel.builder().name("Half").latitude(BigDecimal.valueOf(50)).build();

        assertThatThrownBy(() -> service.create(WORKSPACE, model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longitude is required");
    }

    @Test
    void create_outOfRangeCoordinate_isRejected() {
        var model = LocationModel.builder()
                .name("Off world")
                .latitude(BigDecimal.valueOf(91))
                .longitude(BigDecimal.valueOf(30))
                .build();

        assertThatThrownBy(() -> service.create(WORKSPACE, model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("latitude is out of range");
    }

    @Test
    void create_nullWorkspace_isRejected() {
        assertThatThrownBy(() ->
                service.create(null, LocationModel.builder().name("Depot").build()))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------------------------- update

    @Test
    void update_cannotChangeTheWorkspace() {
        var stored = WorkspaceLocationEntity.builder()
                .uniqueId(LOCATION.getLongValue())
                .workspaceUniqueId(WORKSPACE)
                .name("Before")
                .build();
        when(repository.findById(LOCATION)).thenReturn(Optional.of(stored));

        service.update(LocationModel.builder().uniqueId(LOCATION).name("After").version(0).build());

        verify(repository).save(argThat(entity ->
                WORKSPACE.equals(entity.getWorkspaceUniqueId()) && "After".equals(entity.getName())));
    }

    @Test
    void update_staleVersion_isRejected() {
        var stored = WorkspaceLocationEntity.builder()
                .uniqueId(LOCATION.getLongValue())
                .workspaceUniqueId(WORKSPACE)
                .version(3)
                .name("Before")
                .build();
        when(repository.findById(LOCATION)).thenReturn(Optional.of(stored));

        var stale = LocationModel.builder().uniqueId(LOCATION).name("After").version(1).build();

        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void update_unknownLocation_isRejected() {
        when(repository.findById(LOCATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(LocationModel.builder().uniqueId(LOCATION).name("After").build()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // --------------------------------------------------------------------------------------- reads

    @Test
    void read_returnsTheRequestedLocation() {
        var stored = WorkspaceLocationEntity.builder().uniqueId(LOCATION.getLongValue()).name("Depot").build();
        when(repository.findById(LOCATION)).thenReturn(Optional.of(stored));

        var location = service.read(LOCATION);

        assertThat(location.getName()).isEqualTo("Depot");
    }

    @Test
    void searchByName_isScopedToTheWorkspaceAndBounded() {
        when(repository.findByWorkspaceUniqueIdAndNameContainingIgnoreCase(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.searchByName(WORKSPACE, "  depot  ", 5);

        verify(repository).findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
                eq(WORKSPACE), eq("depot"), eq(PageRequest.of(0, 5, locationNameOrder())));
    }

    @Test
    void searchByName_nonPositiveLimit_fallsBackToTheConfiguredCap() {
        when(repository.findByWorkspaceUniqueIdAndNameContainingIgnoreCase(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.searchByName(WORKSPACE, null, 0);

        verify(repository).findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
                eq(WORKSPACE), eq(""), eq(PageRequest.of(0, MAX_NAME_SEARCH_RESULTS, locationNameOrder())));
    }

    @Test
    void findNearby_isScopedToTheWorkspaceAndSortedNearestFirst() {
        var near = entityAt(50.0000, 30.0000);
        var far = entityAt(50.0030, 30.0000);
        when(repository.findWithinBoundingBox(eq(WORKSPACE), any(), any(), any(), any()))
                .thenReturn(List.of(far, near));

        var matches = service.findNearby(
                WORKSPACE, new ProximityQuery(BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null));

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).distanceMeters()).isLessThan(matches.get(1).distanceMeters());
        verify(repository).findWithinBoundingBox(eq(WORKSPACE), any(), any(), any(), any());
    }

    @Test
    void findNearby_beyondTheRadius_isExcluded() {
        // 400 m configured radius; ~1.1 km away.
        when(repository.findWithinBoundingBox(eq(WORKSPACE), any(), any(), any(), any()))
                .thenReturn(List.of(entityAt(50.0100, 30.0000)));

        var matches = service.findNearby(
                WORKSPACE, new ProximityQuery(BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null));

        assertThat(matches).isEmpty();
    }

    @Test
    void findNearby_nonPositiveRadius_isRejected() {
        var query = new ProximityQuery(BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), 0);

        assertThatThrownBy(() -> service.findNearby(WORKSPACE, query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("radiusMeters must be positive");
    }

    @Test
    void browse_isScopedToTheWorkspace() {
        when(repository.findByWorkspaceUniqueId(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.browse(WORKSPACE, PageRequest.of(0, 10));

        verify(repository).findByWorkspaceUniqueId(eq(WORKSPACE), eq(PageRequest.of(0, 10)));
    }

    private static WorkspaceLocationEntity entityAt(double latitude, double longitude) {
        return WorkspaceLocationEntity.builder()
                .workspaceUniqueId(WORKSPACE)
                .name("At " + latitude)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .build();
    }

    private static Sort locationNameOrder() {
        return Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("uniqueId"));
    }
}
