package vg.rg.geo;

import java.math.BigDecimal;

/**
 * Great-circle distance and a bounding-box helper for proximity suggestions. Kept in plain
 * Java so proximity behaves identically on MySQL and H2 with no spatial-type dependency.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    /** Meters per degree of latitude (near-constant). */
    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private GeoDistance() {
    }

    /** Great-circle distance in meters between two WGS84 coordinates. */
    public static double metersBetween(double lat1, double lng1, double lat2, double lng2) {
        var dLat = Math.toRadians(lat2 - lat1);
        var dLng = Math.toRadians(lng2 - lng1);
        var sinLat = Math.sin(dLat / 2);
        var sinLng = Math.sin(dLng / 2);
        var a = sinLat * sinLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /**
     * Axis-aligned bounding box around a point that fully contains the given radius. Used to prefilter
     * candidates cheaply before exact great-circle-distance refinement. Latitude is clamped to [-90, 90] and
     * longitude to [-180, 180]; antimeridian wrap-around is out of scope for the initial version.
     */
    public static BoundingBox boundingBox(double lat, double lng, int radiusMeters) {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
        var deltaLat = radiusMeters / METERS_PER_DEGREE_LAT;
        var cosLat = Math.cos(Math.toRadians(lat));
        var deltaLng = radiusMeters / (METERS_PER_DEGREE_LAT * Math.max(Math.abs(cosLat), 1e-12));
        return new BoundingBox(
                bd(clampLatitude(lat - deltaLat)),
                bd(clampLatitude(lat + deltaLat)),
                bd(clampLongitude(lng - deltaLng)),
                bd(clampLongitude(lng + deltaLng)));
    }

    private static double clampLatitude(double value) {
        return Math.max(-90d, Math.min(90d, value));
    }

    private static double clampLongitude(double value) {
        return Math.max(-180d, Math.min(180d, value));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    /** Inclusive latitude/longitude bounds for a bounding-box prefilter query. */
    public record BoundingBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    }
}
