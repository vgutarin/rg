package vg.rg.service.workspace;

import vg.rg.model.workspace.WorkspaceModel;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

/**
 * Business operations over workspaces themselves. Every method requires the application-wide workspace
 * permission; methods addressing an existing workspace additionally require <em>ownership</em> of it,
 * which grants complete authority over that workspace and everything inside it.
 *
 * <p>See {@code specs/003-workspace-layer/contracts/workspace-service.md}.
 */
public interface WorkspaceService {

    /** Creates a workspace with a user-supplied name. Never produces a default workspace. */
    WorkspaceModel create(WorkspaceModel model);

    /**
     * Updates a workspace's name and description. This is also how a system-named workspace acquires a
     * real name, after which its label is the stored text in every locale.
     *
     * <p>Uses optimistic concurrency: a stale save is rejected rather than silently overwriting.
     */
    WorkspaceModel update(WorkspaceModel model);

    /**
     * Removes a workspace and everything inside it. Refuses the owner's default workspace, so a
     * permission holder is never left without one.
     *
     * <p>The caller is responsible for confirming with the user first; this method assumes confirmation
     * already happened.
     */
    void delete(UniqueId workspaceId);

    List<WorkspaceModel> listOwned();

    Optional<WorkspaceModel> find(UniqueId workspaceId);

    /**
     * The caller's default workspace, provisioning it on first call so no permission holder is ever
     * without one. The provisioned workspace stores no name: its label is resolved from a message key so
     * it follows the viewer's locale.
     */
    WorkspaceModel ensureDefault();
}
