package vg.rg.service;

import vg.rg.model.WorkspaceModel;
import vg.unique.id.model.UniqueId;

/**
 * Which workspace the caller is currently working in. Stored server-side per user rather than per
 * device, so the selection follows the user wherever they sign in.
 *
 * <p>This is the single entry point the workspace section uses, which is why {@link #activeWorkspace()}
 * repairs a stale pointer instead of failing: a removed or disowned workspace must never produce an
 * error screen.
 */
public interface WorkspaceSelectionService {

    /**
     * The caller's active workspace, provisioning the default when nothing valid is selected and
     * repairing the stored selection.
     */
    WorkspaceModel activeWorkspace();

    /** Makes the given workspace active. Requires ownership of it. */
    void select(UniqueId workspaceId);
}
