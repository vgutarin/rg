package vg.rg.repository;

import vg.unique.id.model.UniqueId;

/**
 * Projection of a query that selects a single {@link UniqueId} column.
 *
 * <p>It exists to keep the identifier out of a repository method's <em>return</em> position. Spring Data
 * treats a returned type that is not the domain type as a DTO projection and tries to match its
 * constructor parameters to the selected properties; {@code UniqueId} comes from a published jar compiled
 * without {@code -parameters}, so that introspection cannot find the names and warns on every call. An
 * interface projection is resolved by accessor name instead, which needs no parameter names — the
 * "avoid its introspection" half of what the warning itself suggests, since the other half would mean
 * recompiling somebody else's artifact.
 *
 * <p>Deliberately neutral in name and shape so any single-identifier query can select {@code as uniqueId}
 * and reuse it, rather than each one growing a near-identical projection.
 */
public interface UniqueIdRow {

    UniqueId getUniqueId();
}
