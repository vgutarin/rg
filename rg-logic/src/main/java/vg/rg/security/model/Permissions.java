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

    public static final class Reports {
        public static final String VIEW = "reports:view";

        private Reports() { }
    }

    public static final class Request {
        public static final String SUBMIT = "request:submit";

        private Request() { }
    }

    public static final class Location {
        public static final String VIEW = "location:view";
        public static final String ADD = "location:add";
        public static final String EDIT = "location:edit";
        public static final String DELETE = "location:delete";

        private Location() { }
    }

    public static final Set<String> ALL = validateAndFreeze(List.of(
            Reports.VIEW,
            Request.SUBMIT,
            Location.VIEW,
            Location.ADD,
            Location.EDIT,
            Location.DELETE));

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
