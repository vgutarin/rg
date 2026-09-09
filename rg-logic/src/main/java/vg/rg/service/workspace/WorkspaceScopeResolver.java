package vg.rg.service.workspace;

import vg.rg.model.security.WorkspaceScope;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Resolves an identifier to the workspace that scopes it. Answers "which workspace does this resource
 * live in", never "may this caller act" — it does not consult the security context at all, which keeps
 * it testable against a real database with no authenticated principal.
 *
 * <p>The declared permission supplies the type, which the caller is already passing: its resource part
 * selects the {@link WorkspaceScopeProvider}, and its verb selects the lookup ({@code create} means the
 * identifier names a container, any other verb means it names the resource). Resolution therefore costs
 * one query and performs no traversal, so there is no depth to bound and no cycle to detect.
 */
public interface WorkspaceScopeResolver {

    /**
     * @param resourceId the identifier being acted upon, or the container for a {@code create} permission
     * @param permission a declared local permission, whose resource part names the type
     * @return the owning workspace, or empty when the permission is claimed by no provider, the
     *         identifier has no matching row, or the row does not join to a workspace. Empty always
     *         means <em>deny</em>; it never falls back to a permission-only decision.
     */
    Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission);
}
