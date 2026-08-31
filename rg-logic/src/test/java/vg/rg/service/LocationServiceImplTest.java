package vg.rg.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.unique.id.model.UniqueId;
import vg.rg.config.GeoProperties;
import vg.rg.entity.LocationEntity;
import vg.rg.mapper.LocationMapper;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.LocationRepository;
import vg.rg.security.AuthorityChecker;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    private static final double QUERY_LAT = 50.0;
    private static final double QUERY_LNG = 30.0;

    @Mock UniqueIdService uniqueIdService;
    @Mock LocationRepository repository;
    @Mock LocationMapper mapper;
    @Mock GeoProperties geoProperties;
    @Mock AuthorityChecker authorityChecker;

    private LocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocationServiceImpl(
                uniqueIdService, repository, mapper, geoProperties, authorityChecker);
        lenient().when(geoProperties.matchRadiusMeters()).thenReturn(500);
        lenient().when(mapper.toModel(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, LocationEntity.class);
            return LocationModel.builder()
                    .name(entity.getName())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .build();
        });
    }

    @Test
    void findNearby_filtersBeyondRadius_andOrdersNearestFirst() {
        var near = location("near", metersNorth(100));
        var mid = location("mid", metersNorth(300));
        var far = location("far", metersNorth(2000));
        when(repository.findWithinBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(far, near, mid));

        var matches = service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(QUERY_LAT), BigDecimal.valueOf(QUERY_LNG), null));

        assertThat(matches).extracting(match -> match.location().getName())
                .containsExactly("near", "mid");
        assertThat(matches.get(0).distanceMeters()).isLessThan(matches.get(1).distanceMeters());
    }

    @Test
    void findNearby_noCandidatesWithinRadius_returnsEmpty() {
        when(repository.findWithinBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(location("far", metersNorth(2000))));

        var matches = service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(QUERY_LAT), BigDecimal.valueOf(QUERY_LNG), null));

        assertThat(matches).isEmpty();
    }

    @Test
    void findNearby_customRadius_overridesDefault() {
        when(repository.findWithinBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(location("mid", metersNorth(300))));

        var withinCustom = service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(QUERY_LAT), BigDecimal.valueOf(QUERY_LNG), 1000));
        var outsideCustom = service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(QUERY_LAT), BigDecimal.valueOf(QUERY_LNG), 100));

        assertThat(withinCustom).hasSize(1);
        assertThat(outsideCustom).isEmpty();
    }

    @Test
    void findNearby_outOfRangeLatitude_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(91), BigDecimal.valueOf(QUERY_LNG), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findNearby_nullLongitude_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.findNearby(new ProximityQuery(
                BigDecimal.valueOf(QUERY_LAT), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_validModel_persistsWithNewUniqueIdAndReturnsSavedModel() {
        var model = writableModel("Cafe", QUERY_LAT, QUERY_LNG, "ChIJ-place");
        var entity = location("Cafe", QUERY_LAT);
        when(mapper.toEntity(model)).thenReturn(entity);
        when(repository.saveWithNewUniqueId(entity, uniqueIdService)).thenReturn(entity);

        var saved = service.create(model);

        assertThat(saved.getName()).isEqualTo("Cafe");
        verify(repository).saveWithNewUniqueId(entity, uniqueIdService);
    }

    @Test
    void create_optionalPlaceIdAbsent_isAccepted() {
        var model = writableModel("Park", QUERY_LAT, QUERY_LNG, null);
        var entity = location("Park", QUERY_LAT);
        when(mapper.toEntity(model)).thenReturn(entity);
        when(repository.saveWithNewUniqueId(entity, uniqueIdService)).thenReturn(entity);

        assertThat(service.create(model).getName()).isEqualTo("Park");
    }

    @Test
    void create_blankName_throwsAndDoesNotPersist() {
        var model = writableModel("  ", QUERY_LAT, QUERY_LNG, null);

        assertThatThrownBy(() -> service.create(model))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveWithNewUniqueId(any(), eq(uniqueIdService));
    }

    @Test
    void create_outOfRangeLatitude_throwsAndDoesNotPersist() {
        var model = writableModel("Cafe", 91.0, QUERY_LNG, null);

        assertThatThrownBy(() -> service.create(model))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveWithNewUniqueId(any(), eq(uniqueIdService));
    }

    @Test
    void create_missingCoordinates_isAccepted() {
        // Coordinates are optional: a location may be added without them (Google Maps unavailable).
        var model = LocationModel.builder().name("Cafe").build();
        var entity = location("Cafe", QUERY_LAT);
        when(mapper.toEntity(model)).thenReturn(entity);
        when(repository.saveWithNewUniqueId(entity, uniqueIdService)).thenReturn(entity);

        assertThat(service.create(model).getName()).isEqualTo("Cafe");
        verify(repository).saveWithNewUniqueId(entity, uniqueIdService);
    }

    @Test
    void create_partialCoordinates_throwsAndDoesNotPersist() {
        // When supplied, coordinates must be a complete pair.
        var model = LocationModel.builder().name("Cafe").latitude(BigDecimal.valueOf(QUERY_LAT)).build();

        assertThatThrownBy(() -> service.create(model))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveWithNewUniqueId(any(), eq(uniqueIdService));
    }

    @Test
    void searchByName_matchingName_returnsMappedModels() {
        when(geoProperties.maxNameSearchResults()).thenReturn(50);
        when(repository.findByNameContainingIgnoreCase(eq("caf"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(location("Cafe", QUERY_LAT))));

        var results = service.searchByName("caf", 0);

        assertThat(results).extracting(LocationModel::getName).containsExactly("Cafe");
    }

    @Test
    void searchByName_blankQuery_searchesAll() {
        when(geoProperties.maxNameSearchResults()).thenReturn(50);
        when(repository.findByNameContainingIgnoreCase(eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.searchByName("   ", 0)).isEmpty();
        verify(repository).findByNameContainingIgnoreCase(eq(""), any(Pageable.class));
    }

    @Test
    void searchByName_limitAboveConfiguredMax_isBoundedByMax() {
        when(geoProperties.maxNameSearchResults()).thenReturn(50);
        when(repository.findByNameContainingIgnoreCase(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var pageable = ArgumentCaptor.forClass(Pageable.class);

        service.searchByName("caf", 1000);

        verify(repository).findByNameContainingIgnoreCase(eq("caf"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void browse_mapsPageContentToModels() {
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(location("Cafe", QUERY_LAT))));

        var page = service.browse(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(LocationModel::getName).containsExactly("Cafe");
    }

    @Test
    void update_currentVersion_appliesEditableFieldsAndSaves() {
        var entity = LocationEntity.builder().uniqueId(1L).version(5)
                .name("Old").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();
        when(repository.findById(new UniqueId(1L))).thenReturn(java.util.Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        var model = LocationModel.builder().uniqueId(new UniqueId(1L)).version(5)
                .name("New").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();

        var updated = service.update(model);

        assertThat(entity.getName()).isEqualTo("New");
        assertThat(updated.getName()).isEqualTo("New");
        verify(repository).save(entity);
    }

    @Test
    void update_staleVersion_throwsOptimisticLockAndDoesNotSave() {
        var entity = LocationEntity.builder().uniqueId(1L).version(6)
                .name("Old").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();
        when(repository.findById(new UniqueId(1L))).thenReturn(java.util.Optional.of(entity));
        var model = LocationModel.builder().uniqueId(new UniqueId(1L)).version(5)
                .name("New").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_missingEntity_throwsEntityNotFound() {
        when(repository.findById(new UniqueId(1L))).thenReturn(java.util.Optional.empty());
        var model = LocationModel.builder().uniqueId(new UniqueId(1L)).version(0)
                .name("New").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();

        assertThatThrownBy(() -> service.update(model)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_blankName_throwsBeforeLoading() {
        var model = LocationModel.builder().uniqueId(new UniqueId(1L)).version(0)
                .name("  ").latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();

        assertThatThrownBy(() -> service.update(model)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findById(any(UniqueId.class));
    }

    @Test
    void delete_existing_deletesEntity() {
        var entity = LocationEntity.builder().uniqueId(1L).version(0).name("X")
                .latitude(BigDecimal.valueOf(QUERY_LAT)).longitude(BigDecimal.valueOf(QUERY_LNG)).build();
        when(repository.findById(new UniqueId(1L))).thenReturn(java.util.Optional.of(entity));

        service.delete(new UniqueId(1L));

        verify(repository).delete(entity);
    }

    @Test
    void delete_missingEntity_throwsEntityNotFound() {
        when(repository.findById(new UniqueId(1L))).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(new UniqueId(1L)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static LocationModel writableModel(String name, double latitude, double longitude, String placeId) {
        return LocationModel.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .googlePlaceId(placeId)
                .build();
    }

    private static LocationEntity location(String name, double latitude) {
        return LocationEntity.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(QUERY_LNG))
                .build();
    }

    private static double metersNorth(double meters) {
        return QUERY_LAT + meters / 111_320d;
    }
}
