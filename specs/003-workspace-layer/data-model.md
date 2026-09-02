# Phase 1 Data Model: Workspace Layer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

All identifiers are `vg.unique.id.model.UniqueId` values issued by the single central generator
(`UniqueIdService.getNext()`), stored as `BIGINT` and mapped with `UniqueIdLongConverter`, following the
convention the retired `LocationEntity` established. No entity stores personal data about a natural person; every user reference
is an opaque abstract identity (Principle I).

---

## Entity: Workspace

`WorkspaceEntity` → table `rg_workspace`. A named container owned by exactly one user.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `uniqueId` | `Long` (`UniqueId`) | PK | From the central generator |
| `version` | `int` | not null, `@Version` | Optimistic concurrency (FR-006) |
| `name` | `String` | **nullable**, 1–128 chars when present | User content, stored verbatim. `NULL` marks a **system-named** workspace whose label is rendered from a message key (FR-046) |
| `description` | `String` | nullable, ≤ 1024 chars | User content; bounded (FR-001) |
| `ownerUniqueId` | `UniqueId` | not null, indexed | The owning user; the scope authority (FR-014) |
| `defaultForOwner` | `UniqueId` | nullable, **unique** | Set to `ownerUniqueId` on the default workspace, `NULL` otherwise (R6) |
| `author` | `UniqueId` | `@CreatedBy`, not updatable | Audit only (FR-022) |
| `lastEditor` | `UniqueId` | `@LastModifiedBy` | Audit only (FR-022) |
| `createdAt` | `Instant` | not null, not updatable, `@CreatedDate` | |
| `updatedAt` | `Instant` | not null, `@LastModifiedDate` | |

**Validation rules**

- A **user-supplied** name is required, trimmed, and rejected when blank or over its bound → localized
  validation message. A user may never save a blank name.
- `name IS NULL` occurs only for workspaces the *system* created — the auto-provisioned default (FR-002)
  and every workspace the migration creates (FR-038). Naming one sets the column and it displays verbatim
  from then on; the transition is one-way (FR-046).
- `description` is optional and rejected when over its bound.
- A user may own at most `rg.workspace.max-per-user` workspaces (default **20**); creation beyond the
  bound is refused (FR-009).
- At most one workspace per owner may carry `defaultForOwner`, enforced by the unique index (R6).

**Lifecycle**

- *Created* on explicit user request, or lazily as the owner's default on first entry into the workspace
  navigation section (FR-002).
- *Updated* — `name`/`description` only; `ownerUniqueId`, `defaultForOwner`, `author`, and `createdAt` are
  immutable after creation. Setting `name` on a system-named workspace is a normal update (FR-046).
- *Removed* — only when `defaultForOwner IS NULL` (FR-008); removal deletes all contained objects (FR-007).

**Relationships**

- One-to-many to every workspace-scoped object type, expressed by that type's own mandatory parent column
  (see *Containment and resolution* below).
- Referenced by at most one `WorkspaceSelection` per user.

---

## Containment and resolution *(no index table, no traversal)*

Containment is expressed by each contained type's **own mandatory parent column** — for locations,
`rg_workspace_location.workspace_unique_id`. There is **no containment-index table** and **no runtime
traversal**: both earlier designs are dropped (research R1).

**Resolving an identifier to its workspace** (FR-035, FR-048):

1. The declared permission's **resource part** names the type — `location:…` → workspace-location,
   `workspace:…` → workspace. Dispatch is a set-membership test against the declared constants
   (`LocalPermissions.Location.contains(permission)`), not string parsing, so a typo cannot masquerade as a
   type.
2. The declared permission's **verb** names which lookup runs. **Container-addressed** verbs are
   `create` (no resource exists yet) and `list` (a collection read is scoped to its container);
   **resource-addressed** verbs are `read`, `update`, and `delete`. The classification is declared next to
   the permissions, not decided per call site.
3. That type's provider runs **one query**, joining through however many levels separate it from the
   workspace, and returns the workspace with its owner.
4. Anything unresolved — no registered type claims the permission, no row for the identifier, or no join to
   a workspace — **denies** and warn-logs (FR-036).

Identifiers are globally unique (one central generator), which is what makes the permission a sufficient
type hint rather than a guess.

**The per-type seam:**

```java
public interface WorkspaceScopeProvider {
    boolean supports(String permission);
    Optional<WorkspaceScope> findByResource(UniqueId resourceId);
    Optional<WorkspaceScope> findByContainer(UniqueId containerId);
}

public record WorkspaceScope(UniqueId workspaceUniqueId, UniqueId ownerUniqueId) { }
```

Two implementations ship: `WorkspaceSelfScopeProvider` (claims `workspace:*`; a workspace resolves to
itself) and `LocationScopeProvider` (claims `location:*`; `findByResource` joins
`rg_workspace_location` → `rg_workspace`, `findByContainer` reads `rg_workspace` directly).

**Cost is flat in depth**: a future `workspace → group → event → comment` chain adds one provider whose
`findByResource` joins four tables in a single query. Adding a type changes no access rule and no existing
call site (FR-017, SC-011, SC-015).

**Implementation note**: `WorkspaceLocationEntity` carries a plain `workspaceUniqueId` column rather than a
`@ManyToOne`, so the join is an ad-hoc entity join (`join WorkspaceEntity w on w.uniqueId = l.workspaceUniqueId`),
supported by the Hibernate version Spring Boot 4 ships. Project the two raw `Long` ids and wrap them in
`UniqueId` inside the provider — a JPQL constructor expression cannot apply `UniqueIdLongConverter`.

**Invariants**

- A contained row's parent column is `NOT NULL`, so every row resolves to a workspace.
- Containment is immutable in this feature — the parent column is never updated (FR-016).
- A workspace is the root; it stores no parent and resolves to itself.

---

## Entity: WorkspaceSelection  *(active workspace, per user)*

`WorkspaceSelectionEntity` → table `rg_workspace_selection`. Durable record of which workspace a user is
currently working in (FR-004).

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `userUniqueId` | `Long` (`UniqueId`) | PK | One row per user — the primary key *is* the uniqueness rule |
| `workspaceUniqueId` | `UniqueId` | not null, FK → `rg_workspace.unique_id` | Must be a workspace the user owns |
| `updatedAt` | `Instant` | not null, `@LastModifiedDate` | |

**Validation rules**

- The referenced workspace MUST be owned by `userUniqueId`; a selection pointing elsewhere is rejected and
  treated as absent.
- Selection is server-side and per user, not per device, so it follows the user across sessions (FR-004).

**Lifecycle / state transitions**

- *Absent* → set to the default workspace on first entry into the workspace section (FR-002).
- *Changed* by the selector in one action (FR-004, SC-003).
- *Repointed* automatically when the selected workspace is removed — the default workspace becomes active
  (FR-008).
- *Stale* selection (workspace gone, or no longer owned) resolves to the default workspace rather than
  erroring.

This entity deliberately registers **no** parent resolver: it is a per-user pointer, not a contained
resource, and it must never appear in a containment chain or be addressable by an authority check.

---

## New entity: WorkspaceLocation

`WorkspaceLocationEntity` → table `rg_workspace_location`. Replaces the former global `rg_location`.
Same business content, plus a **mandatory** workspace reference — a location without a workspace is
unrepresentable (FR-018).

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `uniqueId` | `Long` (`UniqueId`) | PK | From the central generator |
| `version` | `int` | not null, `@Version` | Optimistic concurrency |
| `workspaceUniqueId` | `UniqueId` | **not null**, indexed, FK → `rg_workspace.unique_id` | The scope; immutable after creation (FR-016) |
| `latitude` | `BigDecimal(9,6)` | nullable | Optional, as before |
| `longitude` | `BigDecimal(9,6)` | nullable | Optional, as before |
| `name` | `String` | not null, ≤ 512 | |
| `description` | `String` | nullable, ≤ 2048 | |
| `googlePlaceId` | `String` | nullable, ≤ 512 | |
| `author` | `UniqueId` | `@CreatedBy`, not updatable, not null | Audit only; preserved verbatim by the migration |
| `lastEditor` | `UniqueId` | `@LastModifiedBy` | Audit only |
| `createdAt` | `Instant` | not null, not updatable, `@CreatedDate` | |
| `updatedAt` | `Instant` | not null, `@LastModifiedDate` | |

Indexes mirror the former table (`latitude` for the bounding-box prefilter, `name` for search) with
`workspace_unique_id` leading, since every query is scoped to one workspace:
`ix_rg_workspace_location_ws_lat (workspace_unique_id, latitude)` and
`ix_rg_workspace_location_ws_name (workspace_unique_id, name)`.

**Scope rule**: there is no scope predicate to remember and no discriminator to get wrong — the column is
mandatory, so every query is scoped by construction. This is strictly stronger than the nullable-column
design it replaces (research R4).

The `workspace_unique_id` column serves **both** purposes — scoped querying *and* authority resolution.
`LocationParentResolver.findParent(id)` returns it, which is how a location resolves to its workspace from
its own identifier alone. There is no second row to write and keep consistent (FR-011, research R1).

**Behaviour** otherwise follows [../current/geolocation.md](../current/geolocation.md) unchanged: optional
coordinates, advisory proximity suggestion within `rg.geo.match-radius-meters`, case-insensitive name
search, derived Google Maps link. Proximity matching never looks outside the row's own workspace.

---

## Retired entity: Location (global)

`LocationEntity`, `LocationService`, `LocationsView`, and route `/locations` are **removed** from the
codebase. The **table `rg_location` is retained**, marked unused — no production code path reads or writes
it, and the changelog records a table comment saying it is superseded by `rg_workspace_location`, names the
migration that copied it, and notes that it is retained pending removal. It is the migration's durable
rollback: the source data stays intact indefinitely. Dropping it is a later change, out of scope here
(FR-037, research R10).

The one exception is `LegacyLocationRepository` — a read-only repository over the retained table, used
solely as the migration source. It is the only reader, runs once, and is removed together with the table.

---

## Migration state: marker

`WorkspaceMigrationMarkerEntity` → table `rg_migration_marker` (`name` PK, `completed_at`). One row,
`GLOBAL_LOCATION_TO_WORKSPACE`, written when the migration finishes.

Purpose: make the migration idempotent (FR-040) and give the follow-up drop changeset something concrete
to assert on (research R10). Until the marker is set, the workspace section refuses to present partial
content (FR-041).

**Migration algorithm** (application logic — it needs generated identifiers, so it cannot be schema SQL;
see research R9):

Executed as **one transaction** — it commits in full or rolls back entirely (FR-047):

1. If the marker is set, stop.
2. Read the distinct `author` values from `rg_location` (the column is `NOT NULL`, so no null handling).
3. For each author A: create a default workspace owned by A (`defaultForOwner = A`), unless A already has
   a default workspace.
4. For each author's workspace W and **every** source location L: insert a `rg_workspace_location` row with
   a fresh identifier, `workspaceUniqueId = W`, L's business content, and L's original `author`.
   Result: A × L copies, each an independent record (FR-039).
5. Set the marker (inside the same transaction).

**Atomicity, not resumption**: because everything above is one transaction, a failure at any point rolls
back every workspace, every copy, and the marker — the database is left exactly as it was, and the next
start retries from scratch (FR-041, SC-022). There is nothing to resume and no checkpoint to reason about,
so a partially populated workspace is structurally impossible rather than merely avoided.

**Idempotency** then reduces to the marker check in step 1, plus step 3's existing-default check for the
case where a user was auto-provisioned a default workspace before the migration ran (FR-040).

**Accepted costs** (FR-047): the transaction is held for the whole copy, so a large dataset means a long
transaction, growing undo log, and a startup that blocks until it finishes. There is deliberately **no
cap** — the dataset is the application's own finite existing table, not unbounded inbound data. The
`rg.workspace.migration.enabled` switch is the escape hatch: an operator facing a dataset too large to
migrate at startup sets it to `false` and handles the migration separately.

## Permissions: app-wide and local declarations

Two declarations, one syntax rule (FR-042 – FR-044, research R11).

**`Permissions`** — app-wide, keeps the shared syntax rule and gains the workspace gate:

| Constant | Value | Notes |
|---|---|---|
| `Reports.READ` | `reports:read` | unchanged |
| `Request.SUBMIT` | `request:submit` | unchanged |
| `Workspace.OWNER` | `workspace:owner` | **new** — one permission for the whole workspace lifecycle (FR-028) |

**`LocalPermissions`** — meaningful only inside a workspace. The location capabilities **move here** out of
`Permissions`, and workspace capabilities are added:

| Constant | Value | Status |
|---|---|---|
| `Location.READ` | `location:read` | moved from `Permissions`; reads **one** location, resource-addressed |
| `Location.LIST` | `location:list` | **new** — list/search/proximity in a workspace, container-addressed |
| `Location.CREATE` | `location:create` | moved from `Permissions` |
| `Location.UPDATE` | `location:update` | moved from `Permissions` |
| `Location.DELETE` | `location:delete` | moved from `Permissions` |
| `Workspace.CREATE` | `workspace:create` | **new**, declared only — no call site; a workspace is a root, so it is not container-addressed either |
| `Workspace.READ` | `workspace:read` | **new**, passed by `find`/`select` |
| `Workspace.UPDATE` | `workspace:update` | **new**, passed by `update` |
| `Workspace.DELETE` | `workspace:delete` | **new**, passed by `delete` |

Note `workspace:owner` (app-wide, the layer gate) and `workspace:update` (local, a capability inside a
workspace) are different permissions with different scopes — the shared `workspace:` prefix is
intentional and matches the existing `resource:verb` convention.

**All local permissions are declared-and-validated but not required to be held** in this feature, because
ownership grants complete authority (FR-014, FR-015, research R2). They exist so granular non-owner access
can be added later without editing a call site.

All values match the existing format `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$`, and `LocalPermissions` reuses
`Permissions.hasValidFormat` so validation strength is unchanged (FR-044).

**Each declaration owns its own recognized set — there is no union:**

- `Permissions.isRecognized(p)` → app-wide set only; used by the flat authority check.
- `LocalPermissions.isRecognized(p)` → local set only; used by the resource-scoped check.
- `Permissions.recognized(collection)` → filters against the **app-wide set only**, and is what sanitises
  an incoming principal's permission set for the UI. Local permissions are dropped from it deliberately:
  nothing tests whether they are held, and the sanitised set becomes Spring granted authorities, so
  surfacing a formal permission there would imply an enforcement that does not exist (research R11).

`Permissions` therefore references `LocalPermissions` nowhere, so neither declaration can be left holding
a partial set by class-initialization order.

`AuthenticatedUserPrincipal` continues to validate incoming permissions for **format only**, so it needs
no change. An architecture test asserts no location capability is still referenced from the app-wide
declaration.

---

## Relationship summary

```text
User (abstract identity, external)
 ├─ owns ──────────────► Workspace ──┐
 └─ has one ───────────► WorkspaceSelection ──► Workspace
                                     │
     parent columns on each type │ (workspace is the root; no index table)
                                     ▼
                          ┌──── contained objects, any depth ────┐
                          │  LOCATION            (this feature)  │
                          │  GROUP → EVENT → COMMENT (later)     │
                          └──────────────────────────────────────┘

Retired: rg_location (global, workspace-less) — migrated, then dropped in a follow-up changeset
```

Every location now sits inside a workspace; nothing exists outside one.

## Configuration

| Property | Default | Requirement |
|---|---|---|
| `rg.workspace.max-per-user` | 20 | FR-009 |
| `rg.workspace.name-max-length` | 128 | FR-001 |
| `rg.workspace.description-max-length` | 1024 | FR-001 |
| `rg.workspace.migration.enabled` | true | FR-037; lets an environment skip the one-time migration |

Bound as a `@ConfigurationProperties` record in `rg-logic`, following `GeoProperties`.

## Schema delivery

**One** changelog, under the existing `includeAll: db/liquibase/` master changelog:

`002-workspace-init.yaml` creates `rg_workspace`, `rg_workspace_selection`, `rg_workspace_location`, and
`rg_migration_marker`, and sets a table comment on `rg_location` marking it unused and superseded. It
contains **no `dropTable`** — so applying migrations can never lose the source data.

The one-time data migration runs as application logic *after* Liquibase, because it needs identifiers from
the central generator (research R9). With no drop to order against, that ordering no longer constrains
anything and this feature is a single self-contained deployment (research R10).

**Rollback**: changelog 002 is fully reversible (drop the new tables). The source data in `rg_location` is
never modified, so recovering from a bad migration is a redeploy plus a re-run — not a database restore.
Dropping `rg_location` is a separate later change, made only after the migration has been verified against
real data.
