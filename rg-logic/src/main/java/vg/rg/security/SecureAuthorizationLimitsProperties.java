package vg.rg.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.Objects;

@Component
public final class SecureAuthorizationLimitsProperties {

    public static final String MAX_INIT_DATA_SIZE_PROPERTY =
            "rg.secure-service.max-init-data-size";
    private static final String DEFAULT_MAX_INIT_DATA_SIZE = "32KB";

    private final long maxInitDataBytes;

    public SecureAuthorizationLimitsProperties(Environment environment) {
        Objects.requireNonNull(environment);
        var configuredValue = environment.getProperty(
                MAX_INIT_DATA_SIZE_PROPERTY, DEFAULT_MAX_INIT_DATA_SIZE);
        this.maxInitDataBytes = parsePositiveBytes(configuredValue);
    }

    public long maxInitDataBytes() {
        return maxInitDataBytes;
    }

    private static long parsePositiveBytes(String configuredValue) {
        try {
            var bytes = DataSize.parse(configuredValue).toBytes();
            if (bytes <= 0) {
                throw invalidConfiguration();
            }
            return bytes;
        } catch (RuntimeException exception) {
            throw invalidConfiguration();
        }
    }

    private static IllegalStateException invalidConfiguration() {
        return new IllegalStateException(
                "Invalid configuration for " + MAX_INIT_DATA_SIZE_PROPERTY);
    }
}
