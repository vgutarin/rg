package vg.rg.repository;

import vg.unique.id.model.UniqueId;

/**
 * Projection of a workspace-scope lookup: the owning workspace and its owner, and nothing else — an
 * authority decision needs no more than that, so the query loads no entity.
 *
 * <p>An interface projection rather than an {@code Object[]} tuple: a multi-column tuple wrapped in
 * {@code Optional} is ambiguous to Spring Data, and the untyped array pushed the column order into the
 * caller where it could silently drift.
 */
public interface WorkspaceScopeRow {

    UniqueId getWorkspaceUniqueId();

    UniqueId getOwnerUniqueId();
}
