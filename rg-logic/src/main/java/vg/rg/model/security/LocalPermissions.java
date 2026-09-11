package vg.rg.model.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Capability declarations that only mean something <em>inside</em> a workspace. Distinct from
 * {@link Permissions}, which declares application-wide capabilities, and sharing its syntax rule.
 *
 * <p>A local permission does two jobs. It <strong>identifies the resource's type</strong>, which the
 * resource-scoped authority check uses to pick the lookup that resolves the resource to its workspace —
 * that is what {@code Location.contains(...)} is for, and it is load-bearing. It also records the
 * capability the operation represents, which is <em>formal</em> for now: the check validates that the
 * value is declared but does not require the caller to hold it, because owning the workspace already
 * grants complete authority over everything inside it. The second job exists so that granular non-owner
 * access can be introduced later without editing a single call site.
 *
 * <p>The verb also says <strong>what kind of identifier</strong> travels with the permission, which is
 * how one check can serve both an item and a collection:
 *
 * <ul>
 *   <li><b>Container-addressed</b> — {@code create} (no resource exists yet) and {@code list} (a
 *       collection read is scoped to its container). The identifier names the container.</li>
 *   <li><b>Resource-addressed</b> — {@code read}, {@code update}, {@code delete}. The identifier names
 *       the resource itself.</li>
 * </ul>
 *
 * <p>The distinction lives here rather than at the call site so that a caller cannot get it wrong: the
 * permission and the kind of identifier it expects are declared together.
 */
public final class LocalPermissions {

    /** Capabilities over a saved location inside a workspace. */
    public static final class Location {
        /** Read one location, addressed by its own identifier. */
        public static final String READ = "location:read";
        /** List, search, or match locations within a workspace, addressed by the workspace. */
        public static final String LIST = "location:list";
        public static final String CREATE = "location:create";
        public static final String UPDATE = "location:update";
        public static final String DELETE = "location:delete";

        public static final Set<String> ALL = PermissionSyntax.validateAndFreeze(List.of(
                READ, LIST, CREATE, UPDATE, DELETE));

        /**
         * Whether this permission addresses a location. The type discriminator the authority check
         * dispatches on — a set membership test rather than parsing the string, so a malformed value
         * such as {@code locationn:update} matches nothing instead of being read as a type.
         */
        public static boolean contains(String permission) {
            return permission != null && ALL.contains(permission);
        }

        private Location() { }
    }

    /**
     * Capabilities over a person a workspace owner registered — see {@code WorkspaceParticipantEntity}.
     *
     * <p><strong>Why the resource is {@code workspace-participant} and not {@code participant}</strong>,
     * breaking the shape {@link Location} set: these people are meant to take part in groups and events
     * too, so a second participant-like type is likely, and the bare noun would then have two claimants.
     * {@code location} has no such contested sibling, so it is left as it is rather than renamed for
     * symmetry.
     *
     * <p>{@link #REVEAL_CONTACT} is the one capability here that is not CRUD. It exists because
     * disclosing a phone number in plaintext is a different act from reading the roster, and it must be
     * a separate, deliberate one. Like every local permission it is <em>formal</em> today — owning the
     * workspace already grants it — but it names the boundary now, so granular non-owner access later
     * does not have to invent it.
     */
    public static final class WorkspaceParticipant {
        /** Read one participant, addressed by its own identifier. Excludes the contact number. */
        public static final String READ = "workspace-participant:read";
        /** List a workspace's participants, addressed by the workspace. */
        public static final String LIST = "workspace-participant:list";
        public static final String CREATE = "workspace-participant:create";
        public static final String UPDATE = "workspace-participant:update";
        public static final String DELETE = "workspace-participant:delete";
        /** Disclose one participant's contact number in plaintext, addressed by its own identifier. */
        public static final String REVEAL_CONTACT = "workspace-participant:reveal-contact";

        public static final Set<String> ALL = PermissionSyntax.validateAndFreeze(List.of(
                READ, LIST, CREATE, UPDATE, DELETE, REVEAL_CONTACT));

        /** Whether this permission addresses a participant. See {@link Location#contains(String)}. */
        public static boolean contains(String permission) {
            return permission != null && ALL.contains(permission);
        }

        private WorkspaceParticipant() { }
    }

    /** Capabilities over an event contained in a workspace. */
    public static final class WorkspaceEvent {
        public static final String READ = "workspace-event:read";
        public static final String LIST = "workspace-event:list";
        public static final String CREATE = "workspace-event:create";
        public static final String UPDATE = "workspace-event:update";
        public static final String DELETE = "workspace-event:delete";

        public static final Set<String> ALL = PermissionSyntax.validateAndFreeze(List.of(
                READ, LIST, CREATE, UPDATE, DELETE));

        public static boolean contains(String permission) {
            return permission != null && ALL.contains(permission);
        }

        private WorkspaceEvent() { }
    }

    /**
     * Capabilities over a workspace itself. Distinct from {@link Permissions.Workspace#OWNER}, which is
     * app-wide and gates the layer: these address one particular workspace, which is what lets a single
     * resource-scoped check cover both a workspace and its contents.
     *
     * <p>{@link #CREATE} is declared for completeness but has no call site: creating a workspace has no
     * containing resource to address, so it is guarded by the app-wide check. It is not
     * container-addressed either — a workspace is a root — so nothing special-cases it.
     */
    public static final class Workspace {
        public static final String CREATE = "workspace:create";
        public static final String READ = "workspace:read";
        public static final String UPDATE = "workspace:update";
        public static final String DELETE = "workspace:delete";

        public static final Set<String> ALL = PermissionSyntax.validateAndFreeze(List.of(
                CREATE, READ, UPDATE, DELETE));

        /** Whether this permission addresses a workspace. See {@link Location#contains(String)}. */
        public static boolean contains(String permission) {
            return permission != null && ALL.contains(permission);
        }

        private Workspace() { }
    }

    /** Every declared local permission. */
    public static final Set<String> ALL =
            concat(Location.ALL, concat(WorkspaceParticipant.ALL, concat(WorkspaceEvent.ALL, Workspace.ALL)));

    /**
     * Whether the permission is a declared local one. The resource-scoped authority check accepts only
     * these; an app-wide or unknown value is denied rather than silently treated as scoped.
     */
    public static boolean isRecognized(String permission) {
        return permission != null && ALL.contains(permission);
    }

    /**
     * Whether the identifier accompanying this permission names a <em>container</em> rather than the
     * resource itself — true for {@code create}, which has no resource yet, and for {@code list}, whose
     * subject is a collection scoped to its container.
     *
     * <p>Only <em>contained</em> types appear here. A workspace is a chain root with no container, so
     * {@link Workspace#CREATE} is deliberately absent: there would be nothing for a container lookup to
     * find, and creating a workspace is guarded by the application-wide check instead.
     */
    public static boolean addressesContainer(String permission) {
        return Location.CREATE.equals(permission)
                || Location.LIST.equals(permission)
                || WorkspaceParticipant.CREATE.equals(permission)
                || WorkspaceParticipant.LIST.equals(permission)
                || WorkspaceEvent.CREATE.equals(permission)
                || WorkspaceEvent.LIST.equals(permission);
    }

    private static Set<String> concat(Set<String> first, Set<String> second) {
        var combined = new LinkedHashSet<String>(first);
        for (var permission : second) {
            if (!combined.add(permission)) {
                throw new IllegalStateException("Duplicate local permission declaration: " + permission);
            }
        }
        return Collections.unmodifiableSet(combined);
    }

    private LocalPermissions() { }
}
