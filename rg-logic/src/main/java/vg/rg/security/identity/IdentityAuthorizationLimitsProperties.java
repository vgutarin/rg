package vg.rg.security.identity;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class IdentityAuthorizationLimitsProperties {

    public static final String MAX_PERMISSION_COUNT_PROPERTY =
            "rg.secure-service.identity.max-permission-count";
    public static final String MAX_PERMISSION_LENGTH_PROPERTY =
            "rg.secure-service.identity.max-permission-length";

    private static final int DEFAULT_MAX_PERMISSION_COUNT = 1024;
    private static final int DEFAULT_MAX_PERMISSION_LENGTH = 128;

    private final int maxPermissionCount;
    private final int maxPermissionLength;

    public IdentityAuthorizationLimitsProperties(Environment environment) {
        Objects.requireNonNull(environment);
        maxPermissionCount = positiveInteger(environment, MAX_PERMISSION_COUNT_PROPERTY,
                DEFAULT_MAX_PERMISSION_COUNT);
        maxPermissionLength = positiveInteger(environment, MAX_PERMISSION_LENGTH_PROPERTY,
                DEFAULT_MAX_PERMISSION_LENGTH);
    }

    public int maxPermissionCount() {
        return maxPermissionCount;
    }

    public int maxPermissionLength() {
        return maxPermissionLength;
    }

    private static int positiveInteger(Environment environment, String property, int defaultValue) {
        try {
            var value = environment.getProperty(property);
            var parsed = value == null ? defaultValue : Integer.parseInt(value);
            if (parsed <= 0) {
                throw invalidConfiguration(property);
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw invalidConfiguration(property);
        }
    }

    private static IllegalStateException invalidConfiguration(String property) {
        return new IllegalStateException("Invalid configuration for " + property);
    }
}
