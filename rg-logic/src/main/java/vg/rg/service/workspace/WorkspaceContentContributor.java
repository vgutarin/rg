package vg.rg.service.workspace;

import vg.unique.id.model.UniqueId;

/**
 * How one workspace-scoped type removes its own rows when a workspace is removed.
 *
 * <p>The extension seam for workspace removal: {@code WorkspaceService.delete} iterates every registered
 * contributor, so a future contained type joins removal by adding one implementation and changing no
 * workspace code and no access rule.
 *
 * <p>Pairs with {@code WorkspaceScopeProvider}: a new contained type registers two small things — how it
 * resolves to its workspace, and how it deletes itself for one. Neither is an access rule.
 */
public interface WorkspaceContentContributor {

    /** The resource type this contributor removes, for diagnostics and ordering decisions. */
    String resourceType();

    /**
     * Removes every row of this contributor's type belonging to the given workspace.
     *
     * <p>Runs inside the caller's transaction. MUST delete only rows scoped to that workspace, and MUST
     * NOT touch another workspace's rows.
     */
    void deleteAllInWorkspace(UniqueId workspaceId);
}
