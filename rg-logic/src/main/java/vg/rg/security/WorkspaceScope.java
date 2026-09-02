package vg.rg.security;

import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * The workspace that scopes a resource, with that workspace's owner — everything an authority decision
 * needs about where a resource lives and who controls it.
 *
 * <p>Both values are abstract user/workspace identities; neither is ever resolved to a natural person.
 *
 * @param workspaceUniqueId the workspace containing the resource, at whatever depth
 * @param ownerUniqueId     the workspace's owner, compared against the acting principal
 */
public record WorkspaceScope(UniqueId workspaceUniqueId, UniqueId ownerUniqueId) {

    public WorkspaceScope {
        Objects.requireNonNull(workspaceUniqueId, "workspaceUniqueId");
        Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
    }

    /** Whether the given acting identity owns this workspace. */
    public boolean isOwnedBy(UniqueId userUniqueId) {
        return userUniqueId != null && ownerUniqueId.equals(userUniqueId);
    }
}
