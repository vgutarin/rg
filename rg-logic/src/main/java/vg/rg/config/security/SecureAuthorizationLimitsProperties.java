package vg.rg.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import static vg.rg.config.ConfigurationBounds.positiveBytesOrDefault;

/**
 * Immutable startup bound on the size of opaque authorization input.
 *
 * <p>Bound through {@code @ConfigurationProperties}, but with a {@code String} constructor parameter
 * rather than a {@link DataSize} one: letting Spring convert would report a malformed value as
 * {@code … for value [<the value>]}, and this limit is covered by a test asserting that a rejected value
 * is never disclosed. {@code ConfigurationBounds} parses it instead and names only the key. See
 * {@code ConfigurationBounds} for the full reasoning.
 *
 * <p>Registered explicitly in {@code RgLogicConfig}: {@code @ConfigurationProperties} types are not
 * component-scanned.
 */
@ConfigurationProperties(SecureAuthorizationLimitsProperties.PREFIX)
public final class SecureAuthorizationLimitsProperties {

    static final String PREFIX = "rg.secure-service";

    public static final String MAX_INIT_DATA_SIZE_PROPERTY = PREFIX + ".max-init-data-size";

    private static final DataSize DEFAULT_MAX_INIT_DATA_SIZE = DataSize.ofKilobytes(32);

    /**
     * Largest accepted opaque init-data payload, configured as a {@code DataSize} string such as
     * {@code 32KB}. Must be positive; defaults to 32KB.
     *
     * <p>Named for the property rather than for what it holds — the configuration processor matches a
     * field to its key by name, and this is what puts the sentence above into the generated metadata.
     * The resolved value is in <strong>bytes</strong>, which is what {@link #maxInitDataBytes()} returns.
     */
    private final long maxInitDataSize;

    public SecureAuthorizationLimitsProperties(String maxInitDataSize) {
        this.maxInitDataSize = positiveBytesOrDefault(
                maxInitDataSize, DEFAULT_MAX_INIT_DATA_SIZE, MAX_INIT_DATA_SIZE_PROPERTY);
    }

    public long maxInitDataBytes() {
        return maxInitDataSize;
    }
}
