package vg.rg.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Application-owned workspace configuration. Values come from runtime configuration; sensible defaults
 * keep the layer functional out of the box.
 *
 * <p>There is deliberately no containment-depth bound: an authority check resolves a resource to its
 * workspace with a single permission-dispatched query and performs no runtime traversal, so there is no
 * loop to bound.
 */
@Component
public final class WorkspaceProperties {

    public static final String MAX_PER_USER_PROPERTY = "rg.workspace.max-per-user";
    public static final String NAME_MAX_LENGTH_PROPERTY = "rg.workspace.name-max-length";
    public static final String DESCRIPTION_MAX_LENGTH_PROPERTY = "rg.workspace.description-max-length";

    private static final int DEFAULT_MAX_PER_USER = 20;
    private static final int DEFAULT_NAME_MAX_LENGTH = 128;
    private static final int DEFAULT_DESCRIPTION_MAX_LENGTH = 1024;

    private final int maxPerUser;
    private final int nameMaxLength;
    private final int descriptionMaxLength;

    public WorkspaceProperties(Environment environment) {
        Objects.requireNonNull(environment);
        this.maxPerUser = parsePositiveInt(
                environment, MAX_PER_USER_PROPERTY, DEFAULT_MAX_PER_USER);
        this.nameMaxLength = parsePositiveInt(
                environment, NAME_MAX_LENGTH_PROPERTY, DEFAULT_NAME_MAX_LENGTH);
        this.descriptionMaxLength = parsePositiveInt(
                environment, DESCRIPTION_MAX_LENGTH_PROPERTY, DEFAULT_DESCRIPTION_MAX_LENGTH);
    }

    public int maxPerUser() {
        return maxPerUser;
    }

    public int nameMaxLength() {
        return nameMaxLength;
    }

    public int descriptionMaxLength() {
        return descriptionMaxLength;
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
