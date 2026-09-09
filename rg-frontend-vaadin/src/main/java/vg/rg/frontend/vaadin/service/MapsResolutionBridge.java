package vg.rg.frontend.vaadin.service;

import org.springframework.stereotype.Component;
import vg.rg.model.geo.ProximityMatch;
import vg.rg.model.geo.ProximityQuery;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Server boundary for coordinates acquired in the browser Google Maps picker. Validates the payload and
 * runs the proximity suggestion. The Place ID and coordinates are opaque enrichment data — never trusted
 * as identity.
 */
@Component
public class MapsResolutionBridge {

    /** Upper bound on a stored Google Place ID (see data-model / FR-009). */
    public static final int MAX_PLACE_ID_LENGTH = 512;

    private final WorkspaceLocationService workspaceLocationService;

    public MapsResolutionBridge(WorkspaceLocationService workspaceLocationService) {
        this.workspaceLocationService = Objects.requireNonNull(workspaceLocationService);
    }

    /** Acquired coordinates with an optional Google Place ID (null when the user picked a point only). */
    public record AcquiredCoordinates(BigDecimal latitude, BigDecimal longitude, String googlePlaceId) {
    }

    /**
     * Validates a browser payload. Coordinates are required and range-checked; the Place ID is optional
     * and length-bounded. Blank Place IDs normalize to {@code null}.
     */
    public AcquiredCoordinates validate(Double latitude, Double longitude, String placeId) {
        var lat = requireCoordinate(latitude, "latitude", 90d);
        var lng = requireCoordinate(longitude, "longitude", 180d);
        var normalizedPlaceId = (placeId == null || placeId.isBlank()) ? null : placeId;
        if (normalizedPlaceId != null && normalizedPlaceId.length() > MAX_PLACE_ID_LENGTH) {
            throw new IllegalArgumentException("placeId exceeds maximum length");
        }
        return new AcquiredCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng), normalizedPlaceId);
    }

    /**
     * Validates the payload and returns the advisory proximity suggestion for the acquired coordinates,
     * which never looks outside the given workspace. Authorization happens inside
     * {@link WorkspaceLocationService#findNearby}, which resolves the workspace to its owner.
     *
     * <p>A workspace is always required: there is no unscoped overload to reach for, which is why the
     * former global one was removed rather than left as a convenience.
     */
    public List<ProximityMatch> resolveAndSuggest(UniqueId workspaceId,
                                                  Double latitude, Double longitude, String placeId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var coordinates = validate(latitude, longitude, placeId);
        return workspaceLocationService.findNearby(
                workspaceId, new ProximityQuery(coordinates.latitude(), coordinates.longitude(), null));
    }

    private static double requireCoordinate(Double value, String name, double absoluteBound) {
        if (value == null || Double.isNaN(value) || Math.abs(value) > absoluteBound) {
            throw new IllegalArgumentException(name + " is missing or out of range");
        }
        return value;
    }
}
