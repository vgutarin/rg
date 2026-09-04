package vg.rg.frontend.vaadin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exposes the referrer-restricted Google Maps browser API key to the client connector. The value is
 * injected from runtime configuration ({@code google.maps.browser-api-key}); the placeholder is safe to
 * commit, the value is not. The key is public by design (referrer-scoped) but is still configurable and
 * never logged.
 *
 * <p>Unlike the {@code rg.*} bounds holders this one is a record: both components are {@code String}s,
 * so there is no parsing to keep out of Spring's hands and no accessor type to diverge from the
 * component type. Registered explicitly in {@link AppConfiguration} — {@code @ConfigurationProperties}
 * types are not component-scanned.
 *
 * @param browserApiKey referrer-restricted browser API key, or empty when the picker is unconfigured.
 * @param mapId         optional cloud Map ID. When set to a Vector Map ID, the picker renders the modern
 *                      vector (WebGL) map; when blank, it falls back to the classic raster map. Not a
 *                      secret (exposed to the browser).
 */
@ConfigurationProperties(MapsClientProperties.PREFIX)
public record MapsClientProperties(String browserApiKey, String mapId) {

    static final String PREFIX = "google.maps";

    public static final String BROWSER_API_KEY_PROPERTY = PREFIX + ".browser-api-key";
    public static final String MAP_ID_PROPERTY = PREFIX + ".map-id";

    public MapsClientProperties {
        browserApiKey = trimmedOrEmpty(browserApiKey);
        mapId = trimmedOrEmpty(mapId);
    }

    public boolean isConfigured() {
        return !browserApiKey.isBlank();
    }

    private static String trimmedOrEmpty(String configuredValue) {
        return configuredValue == null ? "" : configuredValue.trim();
    }
}
