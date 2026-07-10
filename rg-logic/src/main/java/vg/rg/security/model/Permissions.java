package vg.rg.security.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class Permissions {

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    public static final class Home {
        public static final String VIEW = "home:view";

        private Home() { }
    }

    public static final class Reports {
        public static final String VIEW = "reports:view";

        private Reports() { }
    }

    public static final class Request {
        public static final String SUBMIT = "request:submit";

        private Request() { }
    }

    public static final Set<String> ALL = validateAndFreeze(List.of(
            Home.VIEW,
            Reports.VIEW,
            Request.SUBMIT));

    public static boolean isRecognized(String permission) {
        return permission != null && ALL.contains(permission);
    }

    public static Set<String> recognized(Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        var recognized = new LinkedHashSet<String>();
        ALL.forEach(permission -> {
            if (permissions.contains(permission)) {
                recognized.add(permission);
            }
        });
        return Collections.unmodifiableSet(recognized);
    }

    static Set<String> validateAndFreeze(Collection<String> permissions) {
        if (permissions == null) {
            throw new IllegalStateException("Permission declarations are required");
        }
        var validated = new LinkedHashSet<String>();
        for (var permission : permissions) {
            if (!hasValidFormat(permission)) {
                throw new IllegalStateException("Invalid permission declaration: " + permission);
            }
            if (!validated.add(permission)) {
                throw new IllegalStateException("Duplicate permission declaration: " + permission);
            }
        }
        return Collections.unmodifiableSet(validated);
    }

    public static boolean hasValidFormat(String permission) {
        return permission != null && FORMAT.matcher(permission).matches();
    }

    private Permissions() { }
}
