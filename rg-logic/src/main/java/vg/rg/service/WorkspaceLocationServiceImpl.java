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
import org.springframework.transaction.annotation.Transactional;
import vg.rg.config.GeoProperties;
import vg.rg.entity.WorkspaceLocationEntity;
import vg.rg.geo.GeoDistance;
import vg.rg.mapper.WorkspaceLocationMapper;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.WorkspaceLocationRepository;
import vg.rg.security.model.LocalPermissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
class WorkspaceLocationServiceImpl implements WorkspaceLocationService {

    private final UniqueIdService uniqueIdService;
    private final WorkspaceLocationRepository repository;
    private final WorkspaceLocationMapper mapper;
    private final GeoProperties geoProperties;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Location.CREATE + "')")
    public LocationModel create(UniqueId workspaceId, LocationModel model) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(model, "model");
        validateWritable(model);

        var entity = mapper.toEntity(model);
        // The scope comes from the operation, never from the model: that is what makes a location's
        // workspace unforgeable from the UI.
        entity.setWorkspaceUniqueId(workspaceId);
        return mapper.toModel(repository.saveWithNewUniqueId(entity, uniqueIdService));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#model.uniqueId, '"
            + LocalPermissions.Location.UPDATE + "')")
    public LocationModel update(LocationModel model) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(model.getUniqueId(), "uniqueId");
        validateWritable(model);

        var entity = repository.findById(model.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != model.getVersion()) {
            // Stale edit: the record advanced since the client loaded it (optimistic concurrency).
            throw new ObjectOptimisticLockingFailureException(
                    WorkspaceLocationEntity.class, model.getUniqueId());
        }
        // Editable fields only. workspaceUniqueId is absent from the model and not updatable on the
        // entity, so a location cannot change workspace; author/createdAt are preserved, and
        // lastEditor/updatedAt/version are maintained by auditing and @Version on save.
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setLatitude(model.getLatitude());
        entity.setLongitude(model.getLongitude());
        entity.setGooglePlaceId(model.getGooglePlaceId());
        return mapper.toModel(repository.save(entity));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#locationId, '"
            + LocalPermissions.Location.DELETE + "')")
    public void delete(UniqueId locationId) {
        Objects.requireNonNull(locationId, "locationId");
        var entity = repository.findById(locationId).orElseThrow(EntityNotFoundException::new);
        repository.delete(entity);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Location.LIST + "')")
    public Page<LocationModel> browse(UniqueId workspaceId, Pageable pageable) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return repository.findByWorkspaceUniqueId(workspaceId, pageable).map(mapper::toModel);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Location.LIST + "')")
    public List<LocationModel> searchByName(UniqueId workspaceId, String query, int limit) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var normalized = query == null ? "" : query.trim();
        var cap = geoProperties.maxNameSearchResults();
        var effectiveLimit = limit > 0 ? Math.min(limit, cap) : cap;
        return repository
                .findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
                        workspaceId, normalized, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Location.LIST + "')")
    public List<ProximityMatch> findNearby(UniqueId workspaceId, ProximityQuery query) {
        Objects.requireNonNull(workspaceId, "workspaceId");
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
        // Scoped to the one workspace: a proximity suggestion never crosses a workspace boundary, so
        // identical copies in other workspaces are invisible here.
        return repository
                .findWithinBoundingBox(workspaceId, box.minLat(), box.maxLat(), box.minLng(), box.maxLng())
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
}
