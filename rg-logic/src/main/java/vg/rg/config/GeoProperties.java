package vg.rg.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Application-owned geolocation configuration. Values come from runtime configuration; sensible
 * defaults keep the module functional out of the box.
 */
@Component
public final class GeoProperties {

    public static final String MATCH_RADIUS_METERS_PROPERTY = "rg.geo.match-radius-meters";
    public static final String MAX_NAME_SEARCH_RESULTS_PROPERTY = "rg.geo.max-name-search-results";

    private static final int DEFAULT_MATCH_RADIUS_METERS = 500;
    private static final int DEFAULT_MAX_NAME_SEARCH_RESULTS = 50;

    private final int matchRadiusMeters;
    private final int maxNameSearchResults;

    public GeoProperties(Environment environment) {
        Objects.requireNonNull(environment);
        this.matchRadiusMeters = parsePositiveInt(
                environment, MATCH_RADIUS_METERS_PROPERTY, DEFAULT_MATCH_RADIUS_METERS);
        this.maxNameSearchResults = parsePositiveInt(
                environment, MAX_NAME_SEARCH_RESULTS_PROPERTY, DEFAULT_MAX_NAME_SEARCH_RESULTS);
    }

    public int matchRadiusMeters() {
        return matchRadiusMeters;
    }

    public int maxNameSearchResults() {
        return maxNameSearchResults;
    }

    private static int parsePositiveInt(Environment environment, String property, int defaultValue) {
        var configuredValue = environment.getProperty(property);
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }
        try {
            var value = Integer.parseInt(configuredValue.trim());
            if (value <= 0) {
                throw invalidConfiguration(property);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalidConfiguration(property);
        }
    }

    private static IllegalStateException invalidConfiguration(String property) {
        return new IllegalStateException("Invalid configuration for " + property);
    }
}
