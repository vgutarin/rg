package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.entity.LocationEntity;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.LocationRepository;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-backed functional tests for {@link LocationServiceImpl} against MySQL. Holds all location-service
 * func coverage (proximity suggestion, create, name search; update/delete concurrency arrives with
 * User Story 6).
 */
class LocationServiceFuncTest extends BaseFuncTest {

    private static final UniqueId AUTHOR = new UniqueId(9001L);
    private static final double BASE_LAT = 50.0;
    private static final double BASE_LNG = 30.0;

    @Autowired
    private LocationService service;

    @Autowired
    private LocationRepository repository;

    @Autowired
    private UniqueIdService uniqueIdService;

    @BeforeEach
    void authenticate() {
        var principal = new AuthenticatedUserPrincipal(
                AUTHOR, "Test User",
                Set.of(Permissions.Location.VIEW, Permissions.Location.ADD),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    // --- Proximity suggestion (User Story 1) ---

    @Test
    void findNearby_within100Meters_suggestsBasePlace() {
        seed(BASE_LAT, BASE_LNG, "Base place");

        var matches = service.findNearby(query(metersNorth(100)));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).location().getName()).isEqualTo("Base place");
        assertThat(matches.get(0).distanceMeters()).isLessThanOrEqualTo(500d);
    }

    @Test
    void findNearby_about2Kilometers_returnsNoSuggestion() {
        seed(BASE_LAT, BASE_LNG, "Base place");

        assertThat(service.findNearby(query(metersNorth(2000)))).isEmpty();
    }

    @Test
    void findNearby_justOutside500Meters_isExcluded() {
        seed(BASE_LAT, BASE_LNG, "Base place");

        assertThat(service.findNearby(query(metersNorth(510)))).isEmpty();
    }

    // --- Create (User Story 3) ---

    @Test
    void create_validModel_persistsWithNewUniqueIdAuthorAndInitialVersion() {
        var created = service.create(LocationModel.builder()
                .name("Central Cafe")
                .description("By the fountain")
                .latitude(BigDecimal.valueOf(BASE_LAT))
                .longitude(BigDecimal.valueOf(BASE_LNG))
                .googlePlaceId("ChIJ-central-cafe")
                .build());

        assertThat(created.getUniqueId()).isNotNull();
        assertThat(created.getVersion()).isZero();
        assertThat(created.getAuthor()).isEqualTo(AUTHOR);
        assertThat(created.getLastEditor()).isEqualTo(AUTHOR);
        assertThat(created.getCreatedAt()).isNotNull();

        var persisted = repository.findById(created.getUniqueId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Central Cafe");
        assertThat(persisted.getGooglePlaceId()).isEqualTo("ChIJ-central-cafe");
        assertThat(persisted.getAuthor()).isEqualTo(AUTHOR);
    }

    // --- Name search (User Story 4) ---

    @Test
    void searchByName_matchingFragment_returnsOnlyMatchesCaseInsensitively() {
        seed(BASE_LAT, BASE_LNG, "Central Cafe");
        seed(BASE_LAT, BASE_LNG, "River Park");

        var results = service.searchByName("caf", 0);

        assertThat(results).extracting(LocationModel::getName).containsExactly("Central Cafe");
    }

    @Test
    void searchByName_blankQuery_returnsAll() {
        seed(BASE_LAT, BASE_LNG, "Central Cafe");
        seed(BASE_LAT, BASE_LNG, "River Park");

        assertThat(service.searchByName("  ", 0)).hasSize(2);
    }

    @Test
    void searchByName_noMatch_returnsEmpty() {
        seed(BASE_LAT, BASE_LNG, "Central Cafe");

        assertThat(service.searchByName("no-such-place", 0)).isEmpty();
    }

    // --- Browse (User Story 5) ---

    @Test
    void browse_returnsAllLocationsAsPage() {
        seed(BASE_LAT, BASE_LNG, "Central Cafe");
        seed(BASE_LAT, BASE_LNG, "River Park");

        var page = service.browse(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(LocationModel::getName)
                .containsExactlyInAnyOrder("Central Cafe", "River Park");
    }

    // --- Update / delete + optimistic concurrency (User Story 6) ---

    @Test
    void update_currentVersion_persistsChangesBumpsVersionAndPreservesAuthor() {
        var created = service.create(newModel("Original"));

        var updated = service.update(edit(created, "Renamed"));

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getVersion()).isGreaterThan(created.getVersion());
        assertThat(updated.getAuthor()).isEqualTo(AUTHOR);
        assertThat(updated.getLastEditor()).isEqualTo(AUTHOR);
    }

    @Test
    void update_staleVersion_failsWithOptimisticLock() {
        var created = service.create(newModel("Original"));
        service.update(edit(created, "First edit")); // uses version 0 → succeeds → version advances

        assertThatThrownBy(() -> service.update(edit(created, "Second edit"))) // stale version 0
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repository.findById(created.getUniqueId()).orElseThrow().getName())
                .isEqualTo("First edit");
    }

    @Test
    void delete_existing_removesLocation() {
        var created = service.create(newModel("To remove"));

        service.delete(created.getUniqueId());

        assertThat(repository.findById(created.getUniqueId())).isEmpty();
    }

    private LocationModel newModel(String name) {
        return LocationModel.builder()
                .name(name)
                .latitude(BigDecimal.valueOf(BASE_LAT))
                .longitude(BigDecimal.valueOf(BASE_LNG))
                .build();
    }

    private LocationModel edit(LocationModel base, String name) {
        return LocationModel.builder()
                .uniqueId(base.getUniqueId())
                .version(base.getVersion())
                .name(name)
                .latitude(base.getLatitude())
                .longitude(base.getLongitude())
                .googlePlaceId(base.getGooglePlaceId())
                .build();
    }

    private void seed(double latitude, double longitude, String name) {
        repository.saveWithNewUniqueId(
                LocationEntity.builder()
                        .latitude(BigDecimal.valueOf(latitude))
                        .longitude(BigDecimal.valueOf(longitude))
                        .name(name)
                        .build(),
                uniqueIdService);
    }

    private static ProximityQuery query(double latitude) {
        return new ProximityQuery(BigDecimal.valueOf(latitude), BigDecimal.valueOf(BASE_LNG), null);
    }

    private static double metersNorth(double meters) {
        return BASE_LAT + meters / 111_320d;
    }
}
