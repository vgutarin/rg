# Contract: Workspace Locations

**Module**: `rg-logic` | **Spec**: [../spec.md](../spec.md) | **Research**: [../research.md](../research.md) (R4, R9, R10)

Locations are the one contained type this feature delivers (FR-010), and after this feature they are the
**only** kind of location: the global collection is migrated away and removed (FR-019). This contract
therefore **replaces** the global `LocationService` rather than sitting beside it.

## What is removed

| Removed | Replaced by |
|---|---|
| `LocationService` / `LocationServiceImpl` | `WorkspaceLocationService` / `WorkspaceLocationServiceImpl` |
| `LocationEntity` (the entity) | `WorkspaceLocationEntity` / table `rg_workspace_location` — the old **table** is retained, unused |
| `LocationRepository` | `WorkspaceLocationRepository` |
| `LocationsView`, route `/locations` | `WorkspaceLocationsView`, route `/workspaces/locations` |
| Nav item gated on `location:read` | Nav item inside the workspace section, gated on `workspace:owner` alone ([ui-navigation.md](./ui-navigation.md)) |
| `Permissions.Location.*` | `LocalPermissions.Location.*` (FR-042) |

`LocationModel`, `ProximityMatch`, `ProximityQuery`, `LocationMapper`, `GeoDistance`, and `GeoProperties`
are **reused unchanged** — the geolocation behaviour in
[../../current/geolocation.md](../../current/geolocation.md) is preserved; only its scope changes.

The **table** `rg_location` is **retained**, marked unused via a changelog table comment — no production
code reads it, and nothing in this feature drops it. It is the migration's durable rollback. Dropping it is
a later change, out of scope here (FR-037, research R10).

## `vg.rg.service.WorkspaceLocationService`

```java
public interface WorkspaceLocationService {

    LocationModel create(UniqueId workspaceId, LocationModel model);

    LocationModel update(LocationModel model);

    void delete(UniqueId locationId);

    Page<LocationModel> browse(UniqueId workspaceId, Pageable pageable);

    List<LocationModel> searchByName(UniqueId workspaceId, String query, int limit);

    List<ProximityMatch> findNearby(UniqueId workspaceId, ProximityQuery query);
}
```

Every method takes either the workspace or a resource identifier, so **no operation can be expressed
without a scope**. There is no unscoped overload to reach for.

### Authorization

All guards use the resource-scoped overload with a **local** permission (FR-043):

| Method | Identifier passed | Guard |
|---|---|---|
| `create` | the **workspace** (no resource exists yet) | `hasAuthority(#workspaceId, LocalPermissions.Location.CREATE)` |
| `update` | the **workspace-location's** id | `hasAuthority(#model.uniqueId, LocalPermissions.Location.UPDATE)` |
| `delete` | the **workspace-location's** id | `hasAuthority(#locationId, LocalPermissions.Location.DELETE)` |
| `browse`, `searchByName`, `findNearby` | the **workspace** (the collection is scoped to it) | `hasAuthority(#workspaceId, LocalPermissions.Location.LIST)` |

For `update` and `delete` the caller passes the **location's own identifier**, not its workspace — the
resolver finds the workspace by probing (`rg_workspace` miss → `rg_workspace_location` hit → its
`workspace_unique_id`). Call sites never resolve the workspace themselves (FR-045).

Each guard requires the two enforced conditions — `workspace:owner` and ownership of the resolved
workspace (FR-013). The location capability is **declared and validated but not required to be held**,
because ownership grants complete authority (FR-014, FR-015): an owner holding no `location:*` permission
at all still has full access to their own workspace's locations. It carries the resource type the resolver
dispatches on, which is why it is passed even though it is never tested against holdings.

Passing an app-wide or undeclared permission to these guards **denies**, even for the owner
(FR-043, SC-020).

### Behaviour

**`create`** — validates as the retired global service did (name required; coordinates optional;
description and Google Place ID bounded). Allocates an identifier and writes the location with
`workspaceUniqueId = workspaceId` (FR-011). The mandatory column **is** the containment link — no separate
index row is written, so there is no second write to keep in step (research R1).

**`update`** — updates editable fields only, rejecting a stale `version` with
`ObjectOptimisticLockingFailureException` for the localized "reload and retry" guidance (FR-006). MUST NOT
change `workspaceUniqueId` (FR-016) — and cannot, because the column is not on `LocationModel`.

**`delete`** — removes the location row.

**`browse` / `searchByName` / `findNearby`** — semantics identical to the retired global service (bounded
results, blank query returns all within the page bound, proximity advisory and nearest-first within
`rg.geo.match-radius-meters`), each restricted to the one workspace (FR-012). Proximity matching never
crosses a workspace boundary.

### Scope safety

The scope is **structural, not conditional**: `workspace_unique_id` is `NOT NULL`, and every repository
method takes a workspace or resolves one, so there is no predicate a query can omit. This is the specific
improvement over the nullable-discriminator design this contract replaces — the leakage class is removed
from the schema rather than tested for (research R4). The zero-leakage test (SC-001) remains, now as
regression cover rather than as the primary defence.

Repository surface (`WorkspaceLocationRepository extends UniqueIdJpaRepository<WorkspaceLocationEntity>`):

```java
Page<WorkspaceLocationEntity> findByWorkspaceUniqueId(UniqueId workspaceId, Pageable pageable);
Page<WorkspaceLocationEntity> findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
        UniqueId workspaceId, String name, Pageable pageable);
List<WorkspaceLocationEntity> findWithinBoundingBox(
        UniqueId workspaceId, BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);
void deleteByWorkspaceUniqueId(UniqueId workspaceId);   // used by the content contributor
long countByWorkspaceUniqueId(UniqueId workspaceId);    // used by migration verification

// One-query workspace resolution for the authority check (FR-035). An ad-hoc entity join, since
// the entity carries a plain workspaceUniqueId column rather than a @ManyToOne. Project the raw
// Longs and wrap them in UniqueId inside the provider - a JPQL constructor expression cannot
// apply UniqueIdLongConverter.
//   select l.workspaceUniqueId, w.ownerUniqueId
//     from WorkspaceLocationEntity l
//     join WorkspaceEntity w on w.uniqueId = l.workspaceUniqueId
//    where l.uniqueId = :id
Optional<WorkspaceScopeRow> findWorkspaceScopeByLocationId(UniqueId locationId);
```

## Parent resolution registration

`LocationScopeProvider implements WorkspaceScopeProvider` in
`rg-logic/src/main/java/vg/rg/service/LocationScopeProvider.java`:

- `supports(permission)` returns `LocalPermissions.Location.contains(permission)` — a set-membership test,
  not string parsing, so a malformed value cannot masquerade as this type;
- `findByResource(locationId)` runs **one** query joining `rg_workspace_location` to `rg_workspace`,
  returning the workspace and its owner;
- `findByContainer(workspaceId)` reads the workspace by id — the path taken for `location:create`, whose
  identifier names the container rather than a resource (FR-048).

This single registration is what makes locations resolvable from their own identifier (see
[authority-checker.md](./authority-checker.md)), and it is the only thing a future contained type has to
add.

## `WorkspaceContentContributor` implementation

`LocationWorkspaceContentContributor` returns `resourceType() == "LOCATION"` and implements
`deleteAllInWorkspace` via `deleteByWorkspaceUniqueId`, deleting only rows carrying that workspace id
(SC-004).

## Migration contract

`GlobalLocationMigration` (application logic, not schema SQL — it needs generated identifiers, research R9):

```java
public interface GlobalLocationMigration {
    /** Idempotent. Returns the outcome; a no-op when already complete or when the source table is gone. */
    MigrationOutcome runIfNeeded();
}

public record MigrationOutcome(boolean performed, int workspacesCreated, long locationsCopied) { }
```

**Contract**

- Runs once at startup **inside a single transaction**, guarded by the `GLOBAL_LOCATION_TO_WORKSPACE`
  marker row and by `rg.workspace.migration.enabled` (FR-047).
- Derives users from the **distinct `author` values** on `rg_location` — the only enumerable user set,
  since the application stores no user records (FR-038).
- Creates one default workspace per author (skipping an author who already has one), **with no stored
  name** so its label is localized per viewer (FR-046) — the migration has no user locale to pick from,
  which is one reason system-named workspaces store no name.
- Copies **every** source location into **each** created workspace: `A × L` independent records, each with
  a fresh identifier, `workspaceUniqueId` set to its workspace, and the source's name, description,
  coordinates, Google Place ID, and original `author` preserved (FR-038, FR-039, FR-040).
- Sets the marker inside the same transaction. A failure at any point **rolls the whole thing back** —
  no workspace, no copy, no marker — so the next start retries from scratch and a partially populated
  workspace is impossible rather than merely avoided (FR-041, FR-047, SC-022).
- Commits no partial progress, uses no batching or checkpointing, and caps neither authors nor locations.
  The tradeoff is a long transaction on a large dataset and a startup that blocks for its duration;
  `rg.workspace.migration.enabled=false` is the operator's escape hatch (FR-047).
- Never reads or writes `rg_location` after the marker is set.

**Verification** — the migration logs its `MigrationOutcome` (workspaces created, locations copied) so the
`A × L` result can be checked against `rg_location` in the deployed environment before anyone drops the
source table. Because this feature drops nothing, verification is a human gate on the *later* change rather
than an automated precondition (FR-037, research R10).

## Required test coverage

**Unit** (`MockitoExtension`): validation and mapping per method; `create` sets no workspace other than the
one given; `update` cannot change the workspace; `LocationParentResolver.findParent` returns the row's
workspace and empty for an unknown id.

**Method security** (pattern of the retired `LocationServiceMethodSecurityTest`): each method denies
without authentication, without `workspace:owner`, and when the resolved workspace belongs to another user;
**allows an owner holding no `location:*` permission at all** (SC-006); denies when handed an app-wide or
undeclared permission (SC-020). Note `update`/`delete` are exercised by passing the location id, proving
the dispatched join resolves it, plus a case asserting that a workspace id passed with `location:update` **denies** rather than resolving through the wrong store (SC-023).

**Integration** (MySQL 8): zero cross-workspace leakage across `browse`, `searchByName`, and `findNearby`
(SC-001); workspace removal deletes its locations and leaves other workspaces' counts unchanged (SC-004).

**Migration integration** (MySQL 8): seed `rg_location` with L rows across A distinct authors, run, then
assert exactly A default workspaces and exactly L locations in each — `A × L` in total — with content and
author attribution preserved (SC-017); run again and assert 0 additional workspaces and 0 additional
copies (SC-018); assert an author who already has a default workspace gets no second one; assert a failure
mid-run rolls everything back — **0** workspaces, **0** copies, no marker, source table unmodified
(FR-041, FR-047, SC-022); assert the source rows in `rg_location` are unmodified after a *successful* run
too; assert `rg.workspace.migration.enabled=false` performs nothing and leaves the marker unset.
