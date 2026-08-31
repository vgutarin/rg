package vg.rg.frontend.vaadin.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Exposes the referrer-restricted Google Maps browser API key to the client connector. The value is
 * injected from runtime configuration ({@code google.maps.browser-api-key}); the placeholder is safe to
 * commit, the value is not. The key is public by design (referrer-scoped) but is still configurable and
 * never logged.
 */
@Component
public final class MapsClientProperties {

    public static final String BROWSER_API_KEY_PROPERTY = "google.maps.browser-api-key";
    /**
     * Optional cloud Map ID. When set to a Vector Map ID, the picker renders the modern vector (WebGL)
     * map; when blank, it falls back to the classic raster map. Not a secret (exposed to the browser).
     */
    public static final String MAP_ID_PROPERTY = "google.maps.map-id";

    private final String browserApiKey;
    private final String mapId;

    public MapsClientProperties(Environment environment) {
        Objects.requireNonNull(environment);
        var configured = environment.getProperty(BROWSER_API_KEY_PROPERTY, "");
        this.browserApiKey = configured == null ? "" : configured.trim();
        var configuredMapId = environment.getProperty(MAP_ID_PROPERTY, "");
        this.mapId = configuredMapId == null ? "" : configuredMapId.trim();
    }

    public String browserApiKey() {
        return browserApiKey;
    }

    /** Vector Map ID, or empty for the classic raster map. */
    public String mapId() {
        return mapId;
    }

    public boolean isConfigured() {
        return !browserApiKey.isBlank();
    }
}
