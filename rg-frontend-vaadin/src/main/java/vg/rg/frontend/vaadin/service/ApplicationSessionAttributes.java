package vg.rg.frontend.vaadin.service;

import com.vaadin.flow.server.VaadinSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed, application-owned attributes in the current Vaadin session.
 *
 * <p>All keys are declared here. Registration rejects duplicate stable names so unrelated
 * workflows cannot accidentally share session state.</p>
 */
public final class ApplicationSessionAttributes {
    private static final Map<String, Key<?>> KEYS = new HashMap<>();

    public static final Key<Long> JOIN_SOCIETY_EVENT_ID = defineKey(
            "rg.home.join-society.event-id", Long.class);

    private ApplicationSessionAttributes() { }

    public static <T> void put(Key<T> key, T value) {
        var session = requireCurrentSession();
        session.setAttribute(key.name, key.valueType.cast(Objects.requireNonNull(value, "value")));
    }

    public static <T> boolean isPresent(Key<T> key) {
        return get(key).isPresent();
    }

    public static <T> Optional<T> get(Key<T> key) {
        Objects.requireNonNull(key, "key");
        var session = VaadinSession.getCurrent();
        if (session == null) {
            return Optional.empty();
        }
        var value = session.getAttribute(key.name);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(key.valueType.cast(value));
    }

    /** Returns and removes the value, making a workflow continuation one-time. */
    public static <T> Optional<T> consume(Key<T> key) {
        Objects.requireNonNull(key, "key");
        var session = VaadinSession.getCurrent();
        if (session == null) {
            return Optional.empty();
        }
        var value = session.getAttribute(key.name);
        session.setAttribute(key.name, null);
        return value == null ? Optional.empty() : Optional.of(key.valueType.cast(value));
    }

    private static synchronized <T> Key<T> defineKey(String name, Class<T> valueType) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(valueType, "valueType");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Session attribute name must not be blank");
        }
        if (KEYS.containsKey(name)) {
            throw new IllegalStateException("Duplicate application session attribute: " + name);
        }
        var key = new Key<>(name, valueType);
        KEYS.put(name, key);
        return key;
    }

    private static VaadinSession requireCurrentSession() {
        var session = VaadinSession.getCurrent();
        if (session == null) {
            throw new IllegalStateException("No current Vaadin session");
        }
        return session;
    }

    /** A key can be obtained only from this registry. */
    public static final class Key<T> {
        private final String name;
        private final Class<T> valueType;

        private Key(String name, Class<T> valueType) {
            this.name = name;
            this.valueType = valueType;
        }
    }
}
