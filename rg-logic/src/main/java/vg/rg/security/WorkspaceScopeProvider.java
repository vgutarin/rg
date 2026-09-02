package vg.rg.security;

import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * How one workspace-scoped type resolves an identifier to its owning workspace. The extension seam of
 * the whole layer: adding a contained type means adding one implementation of this interface and
 * changing no access rule and no existing call site.
 *
 * <p>Implementations live beside the type they serve rather than in this package, so security depends
 * only on this interface and never on a concrete domain type.
 *
 * <p>Each lookup MUST be a <strong>single query</strong>, joining through however many levels separate
 * the type from its workspace. That is what keeps an authority check costing one round trip whether the
 * resource sits directly inside a workspace or five levels down.
 */
public interface WorkspaceScopeProvider {

    /**
     * Whether this provider owns the given permission's resource type. Implemented as a set-membership
     * test against the declared constants — for example {@code LocalPermissions.Location.contains(...)}
     * — rather than by parsing the string, so a near-miss such as {@code locationn:update} claims
     * nothing instead of being read as a type.
     */
    boolean supports(String permission);

    /**
     * Resolves an identifier that names a <strong>resource of this type</strong>, used for every verb
     * except {@code create}.
     *
     * @return empty when no such resource exists or it does not join to a workspace — never a guess
     */
    Optional<WorkspaceScope> findByResource(UniqueId resourceId);

    /**
     * Resolves an identifier that names the <strong>container</strong> a new resource of this type would
     * go into, used for the {@code create} verb, where no resource identifier exists yet.
     *
     * @return empty when the container does not exist, or when this type has no container (a workspace
     *         is a root, so its provider returns empty here)
     */
    Optional<WorkspaceScope> findByContainer(UniqueId containerId);
}
