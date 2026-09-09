package vg.rg.model.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The single permission-syntax rule, shared by the app-wide ({@link Permissions}) and local
 * ({@link LocalPermissions}) declarations so that splitting them does not weaken validation.
 *
 * <p>It lives in its own class so that the two declarations are siblings rather than one depending on
 * the other: neither reads the other's statics, so no class-initialization order can leave either
 * holding a partial set. That independence is asserted by a test, and it is what lets each declaration
 * own its own notion of what it recognizes.
 */
final class PermissionSyntax {

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    static boolean hasValidFormat(String permission) {
        return permission != null && FORMAT.matcher(permission).matches();
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

    private PermissionSyntax() { }
}
