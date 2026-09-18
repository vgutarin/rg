package vg.rg.repository.workspace;

import vg.unique.id.model.UniqueId;

/**
 * Projection of a per-event registration count: the event and how many participants it has, and nothing
 * else. An interface projection rather than an {@code Object[]} tuple, for the reason given on
 * {@link WorkspaceScopeRow} — the untyped array pushes column order into the caller where it can drift.
 */
public interface EventRegistrationCountRow {

    UniqueId getUniqueId();

    long getTotal();
}
