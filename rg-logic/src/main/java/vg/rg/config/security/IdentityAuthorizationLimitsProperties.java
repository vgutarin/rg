package vg.rg.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static vg.rg.config.ConfigurationBounds.positiveIntOrDefault;

/**
 * Immutable startup bounds on the permissions parsed out of an identity authorization response.
 *
 * <p>Bound through {@code @ConfigurationProperties} with {@code String} constructor parameters, so that
 * a rejected value is reported by naming the key alone — the disclosure rule these limits are tested
 * against. See {@code ConfigurationBounds} for why the binder does not do the parsing.
 *
 * <p>Registered explicitly in {@code RgLogicConfig}: {@code @ConfigurationProperties} types are not
 * component-scanned.
 */
@ConfigurationProperties(IdentityAuthorizationLimitsProperties.PREFIX)
public final class IdentityAuthorizationLimitsProperties {

    static final String PREFIX = "rg.secure-service.identity";

    public static final String MAX_PERMISSION_COUNT_PROPERTY = PREFIX + ".max-permission-count";
    public static final String MAX_PERMISSION_LENGTH_PROPERTY = PREFIX + ".max-permission-length";

    private static final int DEFAULT_MAX_PERMISSION_COUNT = 1024;
    private static final int DEFAULT_MAX_PERMISSION_LENGTH = 128;

    /** How many permissions a response may carry. Must be positive; defaults to 1024. */
    private final int maxPermissionCount;

    /** Longest accepted single permission string. Must be positive; defaults to 128. */
    private final int maxPermissionLength;

    public IdentityAuthorizationLimitsProperties(
            String maxPermissionCount, String maxPermissionLength) {
        this.maxPermissionCount = positiveIntOrDefault(
                maxPermissionCount, DEFAULT_MAX_PERMISSION_COUNT, MAX_PERMISSION_COUNT_PROPERTY);
        this.maxPermissionLength = positiveIntOrDefault(
                maxPermissionLength, DEFAULT_MAX_PERMISSION_LENGTH, MAX_PERMISSION_LENGTH_PROPERTY);
    }

    public int maxPermissionCount() {
        return maxPermissionCount;
    }

    public int maxPermissionLength() {
        return maxPermissionLength;
    }
}
