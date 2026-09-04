package vg.rg.config;

import org.springframework.util.unit.DataSize;

/**
 * Shared parsing rules for the startup bounds this application reads from configuration.
 *
 * <p>Exists so the {@code @ConfigurationProperties} holders do not each carry their own copy of these
 * rules, which is what the {@code Environment}-reading versions of them did — the same twenty lines
 * appeared in four classes.
 *
 * <p><strong>These helpers take the raw {@code String}, not a bound {@code int} or {@code DataSize}, and
 * that is the whole point.</strong> Letting the binder convert would be less code, but Spring reports a
 * conversion failure as {@code Failed to convert … for value [<the value>]} — it echoes the offending
 * value into the startup exception and the logs. The security limits under {@code rg.secure-service.*}
 * are tested not to disclose a rejected value ({@code failWithoutDisclosure}), and the other holders
 * behaved the same way before they were bound, so the rule is applied uniformly: a bad value is rejected
 * by naming the <em>key</em> and nothing else. Binding the component as {@code String} keeps the
 * conversion — and therefore the message — in our hands.
 *
 * <p>The cost is that a holder using these cannot be a record: a record accessor's type is its
 * component's type, so a {@code String} component could not expose an {@code int}. They are final classes
 * with final fields instead, which is the same immutability by a longer route.
 */
public final class ConfigurationBounds {

    private ConfigurationBounds() {
    }

    /**
     * Parses a positive integer bound, falling back to {@code defaultValue} when nothing is configured.
     *
     * <p>Absent and blank are deliberately treated the same. {@code application.properties} relays every
     * key through a {@code ${ENV_VAR:default}} placeholder, so an environment variable exported empty
     * arrives as a blank value rather than an absent one, and must not take the application down.
     *
     * @throws IllegalStateException if the value is present, non-blank, and not a positive integer. The
     *                               message names the key and deliberately does not echo the value.
     */
    public static int positiveIntOrDefault(String configuredValue, int defaultValue, String property) {
        if (isAbsent(configuredValue)) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(configuredValue.trim());
        } catch (NumberFormatException exception) {
            throw invalidConfiguration(property);
        }
        if (parsed <= 0) {
            throw invalidConfiguration(property);
        }
        return parsed;
    }

    /**
     * Parses a positive {@link DataSize} bound and returns it in bytes, falling back to
     * {@code defaultValue} when nothing is configured. Absent and blank are treated as in
     * {@link #positiveIntOrDefault}.
     *
     * @throws IllegalStateException if the value is present, non-blank, and not a positive data size. The
     *                               message names the key and deliberately does not echo the value.
     */
    public static long positiveBytesOrDefault(
            String configuredValue, DataSize defaultValue, String property) {
        if (isAbsent(configuredValue)) {
            return defaultValue.toBytes();
        }
        long bytes;
        try {
            bytes = DataSize.parse(configuredValue.trim()).toBytes();
        } catch (RuntimeException exception) {
            throw invalidConfiguration(property);
        }
        if (bytes <= 0) {
            throw invalidConfiguration(property);
        }
        return bytes;
    }

    private static boolean isAbsent(String configuredValue) {
        return configuredValue == null || configuredValue.isBlank();
    }

    private static IllegalStateException invalidConfiguration(String property) {
        return new IllegalStateException("Invalid configuration for " + property);
    }
}
