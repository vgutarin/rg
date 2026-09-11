package vg.rg.model.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application-wide permission declarations: capabilities that mean something without naming a resource.
 *
 * <p>Capabilities that only apply <em>inside</em> a workspace live in {@link LocalPermissions}. The two
 * declarations share the syntax rule exposed by {@link #hasValidFormat(String)}, and the two authority
 * checks accept different sets: the flat check takes app-wide permissions only, the resource-scoped check
 * takes local permissions only.
 *
 * <p>This class deliberately knows nothing about the local declaration. Local permissions are never
 * tested against what a principal <em>holds</em> — owning a workspace already grants complete authority
 * over its contents — so they have no business in a sanitised permission set or in the granted authorities
 * derived from one. Keeping them out also leaves exactly one meaning of "recognized" here.
 */
public final class Permissions {

    /** Grants access to application features intended for experiment participants. */
    public static final class Experiment {
        public static final String PARTICIPANT = "experiment:participant";

        private Experiment() { }
    }

    /**
     * The workspace layer's gate. Holding it means the user may own and use workspaces at all; it is
     * distinct from {@link LocalPermissions.Workspace}, whose capabilities apply to a particular
     * workspace. One permission covers the whole workspace lifecycle.
     */
    public static final class Workspace {
        public static final String OWNER = "workspace:owner";

        private Workspace() { }
    }

    /** Permissions the flat, resource-less authority check accepts. */
    public static final Set<String> APP_WIDE = PermissionSyntax.validateAndFreeze(List.of(
            Experiment.PARTICIPANT,
            Workspace.OWNER));

    /**
     * Every permission this class recognizes. Identical to {@link #APP_WIDE}; retained as the name the
     * rest of the application reads.
     */
    public static final Set<String> ALL = APP_WIDE;

    /**
     * Whether the permission is one the flat, resource-less authority check may accept. A
     * <em>local</em> permission returns false: it is meaningless without a resource to apply it to.
     */
    public static boolean isRecognized(String permission) {
        return permission != null && APP_WIDE.contains(permission);
    }

    /**
     * Filters a principal's declared permissions down to the app-wide ones. Local permissions are dropped
     * on purpose: nothing checks whether they are held, so surfacing them here — or in the Spring
     * authorities derived from this set — would imply an enforcement that does not exist.
     */
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

    /** The shared syntax rule; see {@link PermissionSyntax} for why it lives in its own class. */
    public static boolean hasValidFormat(String permission) {
        return PermissionSyntax.hasValidFormat(permission);
    }

    private Permissions() { }
}
