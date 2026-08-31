package vg.rg.model;

import java.math.BigDecimal;

/**
 * Coordinates acquired in the add flow (from the Google Maps picker, or the Telegram fallback) to run
 * the proximity suggestion against. {@code radiusMeters} is optional; when null the configured default
 * (see {@code GeoProperties}) applies.
 */
public record ProximityQuery(BigDecimal latitude, BigDecimal longitude, Integer radiusMeters) {
}
