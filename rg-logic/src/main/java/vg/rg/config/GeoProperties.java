package vg.rg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static vg.rg.config.ConfigurationBounds.positiveIntOrDefault;

/**
 * Application-owned geolocation configuration. Values come from runtime configuration; sensible
 * defaults keep the module functional out of the box.
 *
 * <p>Bound through {@code @ConfigurationProperties} rather than read from {@code Environment}: binding
 * brings relaxed matching, so a deployment can supply {@code RG_GEO_MATCH_RADIUS_METERS} as an
 * environment variable with no extra code, and it puts the keys in the generated configuration metadata
 * so an IDE can complete them. The fields are final — nothing in the context can retune the search radius
 * after startup.
 *
 * <p>The constructor takes {@code String}s and {@link ConfigurationBounds} does the parsing, so a
 * rejected value is never echoed into the startup exception; see that class for the full reasoning, and
 * for why this is a final class rather than a record.
 *
 * <p>Registered explicitly in {@code RgLogicConfig}: {@code @ConfigurationProperties} types are not
 * component-scanned.
 */
@ConfigurationProperties(GeoProperties.PREFIX)
public final class GeoProperties {

    static final String PREFIX = "rg.geo";

    private static final int DEFAULT_MATCH_RADIUS_METERS = 500;
    private static final int DEFAULT_MAX_NAME_SEARCH_RESULTS = 50;

    /**
     * Radius in meters within which an existing location counts as a match for a proximity query that
     * does not supply its own radius. Must be positive; defaults to 500.
     */
    private final int matchRadiusMeters;

    /**
     * Upper bound on the number of results a name search returns, applied on top of any caller-supplied
     * limit. Must be positive; defaults to 50.
     */
    private final int maxNameSearchResults;

    public GeoProperties(String matchRadiusMeters, String maxNameSearchResults) {
        this.matchRadiusMeters = positiveIntOrDefault(
                matchRadiusMeters, DEFAULT_MATCH_RADIUS_METERS, PREFIX + ".match-radius-meters");
        this.maxNameSearchResults = positiveIntOrDefault(
                maxNameSearchResults, DEFAULT_MAX_NAME_SEARCH_RESULTS, PREFIX + ".max-name-search-results");
    }

    public int matchRadiusMeters() {
        return matchRadiusMeters;
    }

    public int maxNameSearchResults() {
        return maxNameSearchResults;
    }
}
