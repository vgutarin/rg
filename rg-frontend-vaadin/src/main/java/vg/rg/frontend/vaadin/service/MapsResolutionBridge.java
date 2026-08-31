package vg.rg.frontend.vaadin.service;

import org.springframework.stereotype.Component;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.rg.service.LocationService;

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

    private final LocationService locationService;

    public MapsResolutionBridge(LocationService locationService) {
        this.locationService = Objects.requireNonNull(locationService);
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
     * Validates the payload and returns the advisory proximity suggestion for the acquired coordinates.
     * Authorized against {@code location:view} inside {@link LocationService#findNearby}.
     */
    public List<ProximityMatch> resolveAndSuggest(Double latitude, Double longitude, String placeId) {
        var coordinates = validate(latitude, longitude, placeId);
        return locationService.findNearby(
                new ProximityQuery(coordinates.latitude(), coordinates.longitude(), null));
    }

    private static double requireCoordinate(Double value, String name, double absoluteBound) {
        if (value == null || Double.isNaN(value) || Math.abs(value) > absoluteBound) {
            throw new IllegalArgumentException(name + " is missing or out of range");
        }
        return value;
    }
}
