# Phase 0 Research: Workspace Layer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-02

All Technical Context unknowns are resolved below. Every decision records its rationale and the
alternatives that were rejected.

---

## R1 — How to resolve a resource identifier to its workspace

**Decision**: Derive the resource's type from the **declared permission**, then dispatch to that type's own
**single-query** workspace lookup. No probing, no runtime traversal, no index table.

The permission's resource part is the discriminator — `location:update` means the identifier belongs to a
workspace-location, `workspace:update` means it *is* a workspace. Each nested group in `LocalPermissions`
exposes `contains(String)` so the dispatch is a set membership test, not string parsing.

The permission's **verb** then selects which of two lookups runs (FR-048):

| Verb | Identifier names | Lookup |
|---|---|---|
| `create` | the **container** the new object goes into | container → workspace |
| everything else | the **resource** itself | resource → workspace |

**Naming and placement** — the requester asked this to be settled:

```text
vg.rg.security/
  WorkspaceScope.java            record(UniqueId workspaceUniqueId, UniqueId ownerUniqueId)
  WorkspaceScopeProvider.java    per-type seam: supports(permission),
                                 findByResource(id), findByContainer(id)
  WorkspaceScopeResolver.java    interface the checker depends on
  WorkspaceScopeResolverImpl.java  dispatch by permission; deny + warn when unclaimed

vg.rg.service/
  WorkspaceSelfScopeProvider.java   claims workspace:*  — a workspace resolves to itself
  LocationScopeProvider.java        claims location:*   — one join query to the workspace
```

Why this placement: `security` owns the *seam* and the dispatch, so it depends on an interface and never on
a concrete domain type — which is what keeps adding a type from touching the security package. Each
provider lives beside the type it serves in `service`, because it is a `@Component` with behaviour rather
than a Spring Data interface; `repository` stays purely declarative, as it is today. "Scope" rather than
"ancestry" or "parent" because after this change nothing walks a hierarchy — the provider answers "which
workspace scopes this resource", in one step.

**Rationale**:

- **One query, always.** The join chain for a type is written once, statically, in that type's provider. A
  comment five levels down joins comment → event → group → workspace in a single query, so cost is flat in
  depth rather than linear in it. This is the property the probe loop could not give.
- **No depth bound, no cycle handling.** There is no loop to bound and no traversal to cycle, so
  `rg.workspace.max-ancestry-depth` is **deleted** along with the code that would have honoured it. One
  fewer configuration knob and one fewer failure mode.
- **The permission stops being merely formal.** It now carries the dispatch key, which is a much stronger
  reason to pass it at every call site than "it will matter later" (R2, FR-015).
- Identifiers remain globally unique — one central generator (`UniqueIdService.getNext()`, verified in
  `unique-id-api-0.0.2`) — so an identifier still names at most one resource. That is what makes the
  permission a *sufficient* type hint rather than a guess.

**The `create` hole, and why the verb rule exists.** Dispatching on the resource part alone breaks
creation: `hasAuthority(#workspaceId, LocalPermissions.Location.CREATE)` passes a *workspace* identifier
with a `location:*` permission, so a naive dispatch would look for that id in the location store, find
nothing, and deny every location creation. Alternatives were weighed:

| Handling of `create` | Assessment |
|---|---|
| **Verb selects the lookup** (chosen) | One method, one query, and the call site keeps naming the capability it represents. The cost is that the verb carries meaning — documented here and asserted by a test. |
| Guard `create` with the container's own permission (`workspace:update`) | Works and needs no verb rule, but `location:create` would never be used, and the guard would stop describing what the operation does. |
| Fall back to a workspace lookup when the type lookup misses | Reintroduces a probe (two queries) and would silently accept a mismatched permission/identifier pair, which SC-023 exists to catch. |
| A second method for container-addressed checks | The requester explicitly removed the second method; adding another one back is the wrong direction. |

**Risk accepted**: permission and identifier must agree, and a mismatch is now a denial rather than a
compile error. It fails closed and is covered by SC-023, but it is a coupling that did not exist under
probing — the call site is responsible for passing the right pair.

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| Probe each registered type by identifier *(the previous design)* | Superseded. It needed no type hint, but cost up to one query per type per level and required a depth bound and cycle handling. The permission was already at every call site, so the hint was free. |
| A dedicated `rg_resource_node` index walked by one recursive CTE *(two designs ago)* | A second structure every write had to maintain transactionally, with a silent-unreachability failure mode when a row went missing, plus MySQL-specific recursive SQL. |
| Caller passes the resource type explicitly alongside the id | The permission already encodes it; a separate argument could disagree with the permission and would be one more thing to get wrong. |
| Parse the type out of the permission string | `LocalPermissions.Location.contains(permission)` is a set lookup against the declared constants, so a typo cannot masquerade as a type. Parsing would accept `locationn:update` as type `locationn`. |

---

## R2 — The single authority check, and why ownership overrides the permission

**Decision**: One method, `AuthorityChecker.hasAuthority(UniqueId resourceId, String permission)`, guards
every workspace-scoped operation. It takes the identifier of the **resource being acted upon** — for a
location update, the workspace-location's id — and resolves the workspace itself (R1). It returns `true`
only when the permission is a **declared** local permission, a principal is authenticated, the principal
holds the app-wide `workspace:owner`, and the resolved workspace is **owned by** that principal. When those
hold, the operation is permitted **without** checking whether the principal *holds* the declared permission.

**Why ownership overrides the permission value**: resolution has to reach the workspace anyway to establish
scope, so by the time the decision is made, ownership is already known. Requiring a workspace owner to
additionally be granted each capability inside their own strictly-private workspace would be ceremony
without a security benefit — there is no second party whose access the capability would be protecting
against while workspaces are single-owner. The declared permission is therefore *formal* in this feature.

**Why the permission is still passed and still validated**: it is the seam for granular non-owner access.
When sharing arrives, the held-check becomes enforced for non-owners — and because every call site already
names its permission, that change touches this one method and no call site. Meanwhile the value is
validated as a **recognized local permission**, so a typo or a mis-scoped app-wide permission denies rather
than passing unnoticed. The distinction is precise and worth stating: *declared* is enforced, *held* is not.

**One check, not two.** An earlier draft added `hasWorkspaceAuthority(UniqueId)` for operations on a
workspace itself, because `workspace:owner` is app-wide and could not be passed to a check that accepts
only local permissions. That is **dropped**: introducing `LocalPermissions.Workspace` create/read/update/
delete gives workspace operations local permissions of their own, so the single resource-scoped method
covers the workspace and everything inside it. A workspace passed as the resource resolves to itself at
step 1 of R1, with no special case.

**Fail-closed remains the central security property.** All four enforced conditions are conjunctive, and a
resource whose containment cannot be established returns `false`. The tempting alternative — treat an
unresolvable identifier as "not workspace-scoped, so fall back to a permission-only check" — was
**rejected**: an unregistered or orphaned resource would then silently escape workspace scoping. Scope is
chosen by the *caller* (which overload it calls), never inferred from the absence of data:

- app-wide capabilities call `hasAuthority(permission)` with an app-wide permission;
- everything workspace-scoped calls `hasAuthority(resourceId, permission)` with a local permission.

Since the global scope is retired (R4), there is no "unscoped location" case for an unresolvable identifier
to be mistaken for, which makes fail-closed easier to reason about than under the two-scope design.

**Creation is the one case that passes a container**, because no resource identifier exists yet:
`hasAuthority(#workspaceId, LocalPermissions.Location.CREATE)`. This is why FR-011 requires the workspace
binding to be established at creation time.

**Rationale for keeping it on `AuthorityChecker`**: the existing `@PreAuthorize` expressions already read
`@authorityChecker.hasAuthority(...)`, so call sites stay uniform. Resolution lives in a separate
`WorkspaceScopeResolver` collaborator so it can be tested against a real database with no SecurityContext.

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| Enforce the held-permission for owners too | The requester's explicit decision is that ownership overrides it, and there is no second party to protect against in a single-owner workspace. It would also make every owner's setup depend on being granted four `location:*` permissions to use their own private workspace. |
| Drop the permission parameter entirely | It is the seam for non-owner access. Removing it would mean revisiting every call site later — precisely the churn passing it now avoids. |
| Keep a second workspace-only check | Unnecessary once workspace capabilities are local permissions, and two overlapping checks invite guarding with the wrong one. |
| Special-case the scoped check to also accept `workspace:owner` | Reopens the mis-scoping hole FR-043 closes: an app-wide permission would become checkable as though it were scoped. |

---

## R3 — Bounds, failure modes, and caching

**Decision**: There is **no depth bound and no cycle handling**, because there is no runtime traversal to
bound (R1). Resolution is one query per check. Failure resolves to *empty* and therefore **denies**: no
registered type claims the permission, the identifier has no row in the dispatched store, or the row does
not join to a workspace (FR-036). Log a warning carrying only identifiers and the declared permission —
never a principal or a name. **No cache in this feature.**

**Rationale**:

- The constitution requires an explicit bound before unbounded traversal or allocation. A single query with
  a statically written join *is* the bound — the work per check is fixed at compile time, not discovered at
  runtime. `rg.workspace.max-ancestry-depth` is therefore **deleted** rather than defaulted, which is a
  better outcome than tuning a knob nobody can reason about.
- A cycle is unrepresentable: nothing follows a parent reference more than the join chain already encodes.
- Denying on an unclaimed permission or a missing row is the only safe direction, consistent with R2.
- Caching is deferred, and now with less reason to revisit: one query per check does not grow with depth or
  with the number of registered types. The remaining trigger is volume — a screen issuing many checks per
  interaction — for which a **request-scoped memo** of `(resourceId, permission) → WorkspaceScope` is the
  first step and a shared cache the second. `TODO.md` already anticipates a permission cache.

**Alternatives considered**: keeping a depth bound "just in case" (rejected — it would guard a loop that no
longer exists, and dead configuration misleads); accumulating visited ids for cycle detection (rejected for
the same reason); adding the request-scoped memo now (deferred — one indexed query per check does not
justify the state).

---

## R4 — Replacing the global store with a workspace-scoped store

**Decision**: Create a **new** table `rg_workspace_location`, mirroring `rg_location`'s business columns
but with a **mandatory** `workspace_unique_id`. Migrate the existing global rows into it (R9), then remove
`rg_location`, its service, its route, and its navigation entry. `WorkspaceLocationService` becomes the
only location service; there is no global location service and no scope discriminator.

**Rationale**:

- A mandatory column makes "a location without a workspace" unrepresentable, which is strictly stronger
  than the nullable-discriminator design this replaces: there is no predicate for a query to forget, so
  the entire class of cross-scope leakage bugs disappears rather than being tested for.
- One scope means one service, one set of guards, and one mental model. The previous design's value —
  leaving global behaviour untouched — is void once the global scope is retired.
- A new table rather than an altered one keeps the migration non-destructive until it is verified: the
  source data sits untouched in `rg_location` while the copies are written and checked, which is the
  rollback the constitution requires for a breaking change (FR-041).

**Consequences accepted**: `LocationEntity`, `LocationService`, `LocationServiceImpl`, `LocationsView`,
the `/locations` route, and the `location:*` nav gate are all deleted rather than adapted. Their tests
either move to the workspace-scoped equivalents or are removed with them. This is a larger diff than the
nullable-column approach, and it is the point — the ambiguity is removed from the schema instead of being
managed in queries.

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| Nullable `workspace_unique_id` on `rg_location`, both scopes coexisting *(the previous design)* | Superseded by the decision to retire the global scope. It also carried a permanent leakage risk: every query needed the right predicate, forever. |
| Keep `rg_location`, make the column mandatory, backfill in place | Destroys the source data before verification, so there is no rollback window. Also cannot express the one-row-per-(author, location) fan-out the migration needs, since a row can only carry one workspace. |
| Keep the table name and add the column | The name would no longer describe the contents, and the migration needs source and target to exist at once. |

---

## R5 — Structurally confining the selector to the workspace section

**Decision**: Introduce a nested Vaadin router layout, `WorkspaceLayout`, annotated
`@ParentLayout(MainView.class)`. It renders the active-workspace selector and hosts every workspace route
as a child (`@Route(value = "...", layout = WorkspaceLayout.class)`). Views outside the section keep using
`MainView` directly.

**Rationale**: FR-032 becomes a structural property rather than a rule each view must remember — the
selector cannot appear outside the section because it is not in any other layout. It also gives the
section a single place to resolve the active workspace and to lazily provision the default one (FR-002).

**Alternatives considered**: putting the selector in `MainView` and toggling visibility per route
(rejected — every new global route becomes a chance to leak the selector, and it contradicts making the
rule structural); repeating the selector in each workspace view (rejected — duplication, and the selector
state would have to be re-synchronised per view).

---

## R6 — Guaranteeing exactly one default workspace per owner

**Decision**: Enforce it in the schema with a nullable, unique marker column on `rg_workspace`:
`default_for_owner BIGINT NULL UNIQUE`, set to the owner's identifier on the default workspace and `NULL`
on every other workspace.

**Rationale**: MySQL has no partial/filtered unique indexes, and a unique index on
`(owner_unique_id, is_default)` would wrongly forbid a second *non*-default workspace. The nullable marker
gives exactly the intended constraint — at most one default row per owner — because MySQL unique indexes
ignore `NULL`s. This closes the concurrent-provisioning race in FR-002 at the database level rather than
relying on application-level checking.

**Alternatives considered**: an application-level check-then-insert (rejected — races between two sessions
of the same user, precisely the edge case the spec calls out); a separate `rg_workspace_default` table
(rejected — a whole table to express one constraint).

---

## R7 — Removing a workspace's contents without hardcoding its types

**Decision**: Define a narrow `WorkspaceContentContributor` interface in `rg-logic` with one method per
concern — remove all objects of the contributor's type in a given workspace — and one implementation for
locations. Workspace removal iterates the injected contributors, then removes the workspace.

**Rationale**: FR-017 and SC-011 require a new type to join without touching the access rules or the
removal logic; a contributor list is the smallest construct that delivers that. Since the containment index
is gone (R1), there is no parallel structure to clean up either — deleting a type's rows for a workspace is
the whole job. A future nested type deletes its own descendants inside its contributor.

Note this pairs with the `WorkspaceScopeProvider` registration from R1: a new contained type registers
**two** small things — how it resolves to its workspace, and how it deletes itself for a workspace.
Neither is an access rule, so SC-011 still holds.

**Alternatives considered**: calling the location repository directly from the workspace service
(rejected — every future type would edit workspace-removal code, contradicting SC-011); database-level
`ON DELETE CASCADE` on the domain tables themselves (rejected — hides business deletion from the
business-logic module, and JPA would not see it, so audit and validation would be bypassed).

---

## R8 — Testing depth-independence before deep types exist

**Decision**: Prove FR-034/SC-015 now by registering a **synthetic `WorkspaceScopeProvider`** in the test
context that claims a test-only permission and resolves a 5-level chain
(workspace → group → event → comments → comment) in one query, then asserting that the workspace owner is
allowed for **any** identifier in the chain while a non-owner is denied for every one — and that each check
issues exactly **one** query (SC-015).

**Rationale**: the mechanism must be proven at depth even though this feature ships one contained type at
depth 1. A test-only provider exercises the real dispatch, the real ownership decision, and the real
single-query guarantee without inventing entities the spec puts out of scope. It also demonstrates SC-011
directly, because registering one provider is the entire cost of adding a type. Real locations cover the
production join path in the same suite.

**Alternatives considered**: waiting for Groups/Events to exist (rejected — leaves the central requirement
unverified in the release that introduces it); unit tests with a fully mocked resolver only (rejected —
the dispatch and the join are the things under test, and a mock verifies neither); creating throwaway
entities and tables for the deep types (rejected — heavier than a test-only provider and would pull
out-of-scope types into the schema).

---

## R9 — Migrating the shared collection into per-author workspaces

**Decision**: For each **distinct author** on existing `rg_location` rows, create one default workspace,
then copy **every** existing location into **each** of those workspaces as an independent record with a
freshly generated identifier and its own containment row. Preserve each copy's business content and its
original author attribution. Run it as **application logic**, idempotently, and gate the removal of
`rg_location` on verified completion.

**Rationale**:

- The former collection was *shared*: any permitted user could see every location, and the specification
  it implemented states outright that ownership was never used for access. Partitioning it by author would
  therefore **remove** access users already had. A full copy per author is the only partition that
  preserves what each migrated user could previously see.
- `author` is `NOT NULL` on `rg_location`, so "distinct authors" is well defined with no null handling.
- Copies must be independent records (FR-039) because they now live under different owners; keeping them
  linked would recreate shared mutable state inside a model whose whole purpose is isolation.

**Why this cannot be pure schema SQL** — the finding that shapes the whole sequencing: every identifier
comes from `UniqueIdService.getNext()`, a single central allocator backed by its own position tables.
The migration needs fresh identifiers for A workspaces, A×L locations, and their containment rows.
Allocating those in raw SQL would bypass — and risk corrupting — that allocator, so the migration **must**
run through the application. Spring Boot runs Liquibase during datasource initialisation, i.e. **before**
any application runner, so a `dropTable rg_location` changeset shipped in the same changelog would execute
*before* the migration had a chance to run.

**Therefore the removal is not part of this feature at all** (see R10). This feature creates the new store,
runs the migration, and stops reading `rg_location`; the table stays, marked unused, and a later change
drops it once the migration has been verified against real data. The retained table is the rollback plan,
not an oversight — and with no drop to sequence, this feature is a single deployment.

**Atomicity**: the whole migration is **one transaction** — every workspace, every copy, and the
completion marker commit together or not at all (FR-047). A failure at any point rolls back to the exact
prior state and the next start retries from scratch. This makes "no user sees a partially populated
workspace" (FR-041) a structural property rather than a behaviour to get right, and it removes checkpoint
and resumption logic entirely.

The tradeoff, accepted: the transaction is held for the full copy, so a large dataset means a long
transaction, a growing undo log, and a startup that blocks until it finishes. There is deliberately no cap
— this is the application's own finite existing table, not unbounded inbound data, so the constitution's
bound-before-unbounded-parsing rule is satisfied by the dataset being fixed and known. The
`rg.workspace.migration.enabled` switch is the escape hatch for a dataset too large to migrate at startup.

**Idempotency** reduces to the marker check plus a per-author existing-default check (for a user
auto-provisioned before the migration ran). A re-run after success creates neither a second workspace nor a
second copy (FR-040, SC-018).

**Costs, accepted and recorded**: the row count becomes A × L, and the copies diverge as soon as anyone
edits one. For a small collection this is unremarkable; for a large one it multiplies storage. Both are
inherent to converting a shared collection into private workspaces and were accepted deliberately.

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| Assign each location only to its own author's workspace (no duplication) | Every user loses the locations authored by others — an access regression on data they could previously read. |
| One shared workspace containing everything, co-owned by all authors | Workspaces are single-owner in this feature; co-ownership is explicitly out of scope. |
| Migrate nothing and start empty | Silent data loss of the entire existing collection. |
| Liquibase `customChange` calling into Spring for identifiers | Technically possible via a static context holder, but it makes schema migration depend on application bean wiring — fragile, hard to test, and hostile to the simplicity mandate. |
| Provision workspaces for all users, not just authors | Impossible: the application deliberately stores no user records, so authors are the only enumerable set (FR-038). |
| Batch inserts with a per-author commit and resumption | Bounds memory and lock duration, but reintroduces partial state — a half-migrated database where some users see a populated workspace and others an empty one — and the checkpoint logic to reason about it. All-or-nothing was chosen instead. |
| Cap the total and refuse above it | Turns a large dataset into a blocked startup with no in-app operator path. The migration toggle covers the same need without a hard refusal. |

---

## R10 — Retaining the old store, marked unused

**Decision**: This feature ships **one** changelog. `002-workspace-init.yaml` creates the new tables; the
migration runs at startup; all reads and writes move to `rg_workspace_location`. **`rg_location` is left in
place, marked unused** — no production code path reads or writes it. Dropping it is a separate, later
change, out of scope here (FR-037).

"Marked unused" means, concretely:

- no entity maps it and no repository targets it, except the read-only migration source
  (`LegacyLocationRepository`), which is the *only* reader and runs once;
- a table comment recorded in the changelog states that it is superseded by `rg_workspace_location`, names
  the migration that copied it, and says it is retained pending removal — so a future reader of the schema
  is not left guessing;
- the changelog carries no `dropTable`, so no environment can lose the data by applying migrations.

**Rationale**:

- It is the **rollback plan** the constitution requires for a breaking change, and a durable one rather
  than a one-deployment window: the source data stays intact indefinitely, so recovering from a bad
  migration is a redeploy plus a re-run, never a database restore.
- It **dissolves the sequencing problem** a drop created. Because the migration needs identifiers from the
  central generator it must run as application logic, which starts *after* Liquibase — so a `dropTable` in
  the same changelog would have deleted the source before the migration could run (R9). With no drop,
  ordering stops mattering and this feature becomes a single self-contained deployment.
- Verification happens against real data in the deployed environment before anything is destroyed, which
  is the right order for an irreversible operation.

**Cost, accepted**: the schema carries a dead table until the follow-up change, and `LegacyLocationRepository`
lingers with it. Both are visible and documented rather than silently orphaned, and the table comment is
what keeps "temporarily retained" from decaying into "nobody remembers why this is here".

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| Drop it in the same changelog | Liquibase runs before application startup, so it would delete the source data before the migration could run. |
| Drop it in a gated follow-up changeset shipped with this feature *(the previous design)* | Superseded by the requester's decision. It worked, but it made this feature span two deployments and put an irreversible step on a schedule rather than behind a human verification. |
| Rename it to `rg_location_migrated_backup` | A rename is itself a schema change that breaks rollback to the previous application version, which still expects `rg_location`. A comment conveys the same intent at no risk. |
| Leave it with no marking at all | The next person reading the schema cannot tell a dead table from a live one, and the constitution asks for documented purpose and lifetime for retained data. |

---

## R11 — Splitting local from app-wide permission declarations

**Decision**: Keep `Permissions` as the **app-wide** declaration (`Reports.READ`, `Request.SUBMIT`,
`Workspace.OWNER`) and introduce `LocalPermissions` for permissions meaningful only inside a workspace:
the location capabilities moved out of `Permissions`, plus a new `LocalPermissions.Workspace`
create/read/update/delete for operations on a workspace itself. The workspace CRUD set is declared and
passed at its call sites now but not yet enforced against what a user holds (R2) — it is what allows the
single resource-scoped check to cover workspace operations, so no second check is needed. Both share one syntax rule. The two
authority checks then validate against **different** sets:

- `hasAuthority(String)` accepts app-wide permissions and **denies** a local one — a local permission has
  no meaning without a resource;
- `hasAuthority(UniqueId, String)` accepts local permissions and **denies** an app-wide one.

**Rationale**:

- The split is what the requester asked for, and it mirrors the global/local distinction already sketched
  in `TODO.md`, so the later ACL work inherits the vocabulary.
- Making each check accept only its own kind turns the split from filing into enforcement (FR-043,
  SC-020). A location capability can no longer be checked without a resource, which is exactly the class
  of mistake that would silently grant workspace content on an app-wide check.
- One shared syntax rule keeps validation strength unchanged (FR-044).

**No union between the declarations.** An earlier draft had the app-wide `recognized(...)` helper span
both, to keep a local permission from being dropped when the UI sanitises a principal's set. That was
wrong on two counts, and it is not what shipped:

- The reason for it evaporated with the ownership-override decision (R2). The only consumer that needed a
  local permission in the sanitised set was a navigation gate on `location:read` — and since owning a
  workspace already grants complete authority over its locations, such a gate would *hide a screen the
  user can actually use*. The gate is gone, and with it the requirement.
- Sanitised permissions become Spring `GrantedAuthority` values. Surfacing a *formal* local permission
  there invites someone to write `@PreAuthorize("hasAuthority('workspace:update')")`, which would pass on
  holdings while the real check ignores holdings entirely — two contradictory semantics for one string.

A union also left two meanings of "recognized" in one class (`isRecognized` app-wide,
`recognized(...)` spanning both), which is a trap for the next reader. So `Permissions` knows nothing
about `LocalPermissions`: each declaration owns its own recognized set, and neither reads the other's
statics. An architecture test asserts that no location capability remains referenced from the app-wide
declaration once the global scope is retired.

**Alternatives considered**: one flat declaration with a naming convention only (rejected — the requester
asked for a separate class, and a convention cannot be enforced by a check); separate declarations with a
single shared recognition set (rejected — organizational only, so the mis-scoping mistake stays possible);
a per-resource-type permission enum (rejected — larger change, and the string form is already load-bearing
in `@PreAuthorize` expressions).

---
