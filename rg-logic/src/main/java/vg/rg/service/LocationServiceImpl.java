package vg.rg.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vg.rg.config.GeoProperties;
import vg.rg.entity.LocationEntity;
import vg.rg.geo.GeoDistance;
import vg.rg.mapper.LocationMapper;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.LocationRepository;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Skeleton implementation. Method bodies are filled in per user story (Phases 3–8); the authorization
 * annotations and dependency wiring are established here (Foundational phase).
 */
@Slf4j
@RequiredArgsConstructor
@Service
class LocationServiceImpl implements LocationService {

    private final UniqueIdService uniqueIdService;
    private final LocationRepository repository;
    private final LocationMapper mapper;
    private final GeoProperties geoProperties;
    private final AuthorityChecker authorityChecker;

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.ADD + "')")
    public LocationModel create(LocationModel model) {
        Objects.requireNonNull(model, "model");
        validateWritable(model);
        var entity = repository.saveWithNewUniqueId(mapper.toEntity(model), uniqueIdService);
        return mapper.toModel(entity);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.EDIT + "')")
    public LocationModel update(LocationModel model) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(model.getUniqueId(), "uniqueId");
        validateWritable(model);

        var entity = repository.findById(model.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != model.getVersion()) {
            // Stale edit: the record advanced since the client loaded it (optimistic concurrency).
            throw new ObjectOptimisticLockingFailureException(LocationEntity.class, model.getUniqueId());
        }
        // Update only editable fields; author/createdAt are preserved, lastEditor/updatedAt/version are
        // maintained by JPA auditing and @Version on save.
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setLatitude(model.getLatitude());
        entity.setLongitude(model.getLongitude());
        entity.setGooglePlaceId(model.getGooglePlaceId());
        return mapper.toModel(repository.save(entity));
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.DELETE + "')")
    public void delete(UniqueId id) {
        Objects.requireNonNull(id, "id");
        var entity = repository.findById(id).orElseThrow(EntityNotFoundException::new);
        repository.delete(entity);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.VIEW + "')")
    public List<ProximityMatch> findNearby(ProximityQuery query) {
        Objects.requireNonNull(query, "query");
        var latitude = requireCoordinate(query.latitude(), "latitude", 90d);
        var longitude = requireCoordinate(query.longitude(), "longitude", 180d);
        var radiusMeters = query.radiusMeters() != null
                ? query.radiusMeters()
                : geoProperties.matchRadiusMeters();
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        var box = GeoDistance.boundingBox(latitude, longitude, radiusMeters);
        return repository.findWithinBoundingBox(box.minLat(), box.maxLat(), box.minLng(), box.maxLng())
                .stream()
                .map(entity -> new ProximityMatch(
                        mapper.toModel(entity),
                        GeoDistance.metersBetween(
                                latitude, longitude,
                                entity.getLatitude().doubleValue(), entity.getLongitude().doubleValue())))
                .filter(match -> match.distanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(ProximityMatch::distanceMeters))
                .toList();
    }

    private static void validateWritable(LocationModel model) {
        if (model.getName() == null || model.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        // Coordinates are optional (a location may be added without them when Google Maps is
        // unavailable), but when supplied they must be a complete pair and within range.
        if (model.getLatitude() != null || model.getLongitude() != null) {
            requireCoordinate(model.getLatitude(), "latitude", 90d);
            requireCoordinate(model.getLongitude(), "longitude", 180d);
        }
    }

    private static double requireCoordinate(BigDecimal value, String name, double absoluteBound) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        var coordinate = value.doubleValue();
        if (Double.isNaN(coordinate) || Math.abs(coordinate) > absoluteBound) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return coordinate;
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.VIEW + "')")
    public List<LocationModel> searchByName(String query, int limit) {
        var normalized = query == null ? "" : query.trim();
        var cap = geoProperties.maxNameSearchResults();
        var effectiveLimit = limit > 0 ? Math.min(limit, cap) : cap;
        return repository
                .findByNameContainingIgnoreCase(normalized, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.VIEW + "')")
    public Page<LocationModel> browse(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toModel);
    }
}
