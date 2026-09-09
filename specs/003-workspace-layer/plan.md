# Implementation Plan: Workspace Layer

**Branch**: `003-workspace-layer` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-workspace-layer/spec.md`

## Summary

Introduce a **workspace** — a private, named container owned by one user — and make objects inside it
derive their access from it. Locations are the one contained type delivered here, and the **global
location scope is retired**: the shared collection is migrated into per-author workspaces and then
removed, along with its route and navigation entry, so a location can only exist inside a workspace.

The technical core is **one** new method, `AuthorityChecker.hasAuthority(UniqueId resourceId, String
permission)`, guarding every workspace-scoped operation. Callers pass the identifier of the **resource
being acted upon** — for a location update, the workspace-location's id — and the checker resolves the
containing workspace itself. The **declared permission supplies the type**: its resource part
(`location:…`, `workspace:…`) selects that type's provider, and its verb selects the lookup — `create`
means the identifier names the container, any other verb means it names the resource. The provider then
resolves to the workspace in a **single query**, joining through however many levels separate the two, so
cost is flat in depth. There is no containment-index table, no probe loop, and consequently no depth
bound.

Two conditions are enforced: the caller holds the app-wide `workspace:owner`, and the caller **owns** the
resolved workspace. When both hold the operation is permitted — **ownership grants complete authority**
over the workspace and everything inside it at any depth, without the caller having to hold the operation's
individual capability. The capability is still declared at every call site and validated as a recognized
local permission, but is *formal* here: it is the seam through which granular non-owner access arrives
later, with no call site changed. Resolution **fails closed** and warn-logs: an unknown identifier, an
identifier matching no known type, an over-deep or cyclic chain all deny rather than falling back to a
permission-only decision.

Capability permissions split to match: `Permissions` keeps app-wide permissions (including the new
`workspace:owner`), while a new `LocalPermissions` holds the location capabilities **and** workspace
create/read/update/delete. Each check accepts only its own kind, so a local permission can never be checked
without a resource. Giving workspace operations local permissions is what lets a **single** scoped check
cover both the workspace and its contents — no separate workspace-only check is needed (FR-045).

The UI adds a permission-gated navigation item and a nested router layout that structurally confines the
active-workspace selector to the workspace section; the locations screen moves under that section.

## Technical Context

**Language/Version**: Java 21 (`sourceCompatibility = '21'`)

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Spring Security (method security via
`@EnableMethodSecurity`), Vaadin 25.2.2, Liquibase, MapStruct 1.6.3, Lombok,
`vg.unique-id:unique-id-api:0.0.2` (+ `unique-id-jpa-lib`)

**Storage**: MySQL 8. Four new tables (`rg_workspace`, `rg_workspace_selection`, `rg_workspace_location`,
`rg_migration_marker`) in a **single** changelog, `002-workspace-init.yaml`, under the existing
`includeAll: db/liquibase/` master changelog. It contains **no `dropTable`**: `rg_location` is retained and
marked unused, and dropping it is a later change (research R10). Resolution uses plain primary-key lookups
per check — **no recursive SQL, no index table, no traversal loop** — so it stays portable. The one-time data
migration runs as **application logic** after Liquibase, because it needs identifiers from the central
generator (research R9).

**Testing**: JUnit 5 + AssertJ; Mockito with `MockitoExtension` for unit tests; `BaseFuncTest`
(`@SpringBootTest`, profiles `test`+`integration`) with `Mysql8ContainerStarter` for integration tests;
`AnnotationConfigApplicationContext` + `@EnableMethodSecurity` for method-security tests, following
`LocationServiceMethodSecurityTest`

**Target Platform**: Server-rendered web application in a Telegram Mini App webview (narrow mobile
viewport first) and desktop browsers

**Project Type**: Multi-module JVM web application — `rg-logic` (business logic) + `rg-frontend-vaadin`
(UI), per the constitution's module-ownership rule

**Performance Goals**: Application-owned work only; no external service is involved beyond the existing
authentication boundary. One authority check costs **exactly one query**, regardless of how many levels
separate the resource from its workspace. No end-to-end latency target is set, consistent with Principle IV.

**Constraints**: Explicit enforceable bounds on workspace count per user, name/description length, and
result pages. Resolution needs no depth bound because it performs no runtime traversal. No personal data anywhere in the layer. All user-facing text
internationalized with a default-locale fallback. Mobile-first layout.

**Scale/Scope**: ~6 new `rg-logic` services/collaborators, 4 new entities, 1 new app-wide permission plus a
new local-permission declaration class (8 constants), 2 new views + 1 new router layout, 1 Liquibase
changelog in a single deployment, and the **removal** of the global location entity, service, view, and
route (its table is retained, unused). Chains are depth 1 today; the mechanism is verified to at least depth 5 via test-only
parent resolvers.

## Constitution Check

*GATE: evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | Gate | Verdict |
|---|---|---|
| **I. Personal-data prohibition** | No personal data persisted, logged, or exposed | **PASS.** Owner, author, and last-editor are opaque `UniqueId`s. Workspace name/description are the user's own business content, treated exactly like location free text with localized guidance (FR-024). Resolver diagnostics log identifiers and depth only — never a principal, name, or content. Contacts, the one type that would have raised a personal-data question, is out of scope with the constraint recorded for later. |
| **II. Secure-service trust boundaries** | Identity delegated; bounds on inputs | **PASS.** No new external integration and no new identity flow; `workspace:owner` arrives in the existing principal from the existing facade. Every input is bounded (name, description, page size, workspace count). Resolution needs no runtime bound: the work per check is one statically written query, fixed at compile time rather than discovered at runtime (FR-035, FR-036). |
| **III. Delegated authorization** | Authorize the abstract ID; never trust client identifiers | **PASS.** Every workspace operation authorizes the principal's abstract ID against the resolved workspace's owner. A client-supplied resource identifier is never trusted as proof of scope — it is resolved server-side, and an unresolvable identifier denies. |
| **IV. Resilient middleware boundaries** | Timeouts, bounded work, clear partial-failure states | **PASS.** No external calls added. Optimistic concurrency yields a recoverable "reload and retry" state (FR-006). Containment is the resource's own mandatory column, so there is no second write to leave inconsistent and no partial-creation state that could widen access. Resolution is a single query with no loop to bound (FR-035). The one-time migration is **all-or-nothing** (FR-047), so its only two states are "not run" and "complete". No end-to-end latency target is imposed. |
| **V. Mobile-first, accessible, i18n** | Narrow-first, full i18n, tested in 2 locales | **PASS.** Required by FR-025/FR-026 and covered by [contracts/ui-navigation.md](./contracts/ui-navigation.md); `rg-logic` returns stable message keys and the UI owns translation. |
| **VI. Module ownership** | Business rules in `rg-logic`; UI holds none | **PASS.** Containment, inheritance, lifecycle, and the authority check live in `rg-logic`; the UI renders and delegates (FR-027). The architecture test forbidding Vaadin/identity types in `rg-logic` continues to hold. |
| **Simplicity mandate** | New persistence/abstractions need a demonstrated need and a documented tradeoff | **PASS, improved.** Dropping the containment-index table removed a table, an entity, a repository, a cascade rule, and a same-transaction invariant; dropping the second authority check removed a method. Four tables and two one-implementation interfaces remain, each tied to a requirement, with alternatives in [research.md](./research.md). Retiring the global scope also *removes* an entity, a service, a repository, a view, and a route. |
| **Breaking change: migration and rollback** | A breaking contract change requires a migration **and** a rollback plan | **PASS, strengthened.** Retiring the global collection is the breaking change. Migration is specified in research R9 and [contracts/workspace-location-service.md](./contracts/workspace-location-service.md). Rollback is **durable rather than time-boxed** (research R10): `rg_location` is retained, marked unused, never modified, and this feature drops nothing — so recovering from a bad migration is a redeploy plus a re-run, never a database restore. Removal is a separate later change, gated on verification against real data. |
| **Test coverage** | Unit **and** integration tests for every production change; contract tests when a boundary changes | **PASS.** The authority boundary changes, so contract-level coverage is mandatory: unit tests per condition, integration tests at depth ≥ 5 against real MySQL, method-security tests per service method, and a zero-leakage scope test. |
| **Specification artifacts** | `specs/current/` actualized; `tasks.md` ends with an actualization phase | **DEFERRED to `/speckit-tasks`,** which must add the final phase creating `specs/current/workspace.md` and **rewriting** `specs/current/geolocation.md` — locations are now workspace-scoped only, so its "shared collection" framing is superseded rather than merely extended. Recorded here so it is not lost. |

**Post-Phase-1 re-evaluation**: no gate changed verdict, and two improved. Replacing the nullable scope
discriminator with a mandatory column removed a permanent leakage risk — there is no predicate left for a
query to forget. Dropping the containment index removed a second structure that every write had to maintain
transactionally, and with it the failure mode where a missing index row makes a live resource unreachable.
The design still adds no external dependency, no asynchronous infrastructure, and no personal data.

One consequence of ownership overriding the declared capability is worth a reviewer's note: within a
single-owner workspace the `location:*` and `workspace:*` local permissions are not enforced against what a
user holds. That is deliberate (research R2) and safe only while workspaces are strictly single-owner —
introducing sharing **must** enable the held-check for non-owners before any non-owner can reach a
workspace.

The items worth a reviewer's attention are now: (1) the **irreversible data migration** and its A × L
fan-out, gated per research R9/R10; and (2) the fact that non-author users receive an empty workspace and
lose sight of the previously shared collection — an accepted consequence of moving from a shared
collection to private workspaces, recorded in the spec's edge cases and assumptions.

## Project Structure

### Documentation (this feature)

```text
specs/003-workspace-layer/
├── plan.md                              # This file
├── spec.md                              # Feature specification
├── research.md                          # Phase 0 — R1–R8 decisions
├── data-model.md                        # Phase 1 — entities, bounds, schema
├── quickstart.md                        # Phase 1 — how to run and verify
├── contracts/
│   ├── authority-checker.md             # The security boundary (new method + resolver)
│   ├── workspace-service.md             # Workspace lifecycle + selection + content contributor
│   ├── workspace-location-service.md    # Workspace-scoped locations; global coexistence
│   └── ui-navigation.md                 # Nav gating, section layout, selector confinement
├── checklists/
│   └── requirements.md                  # Spec quality checklist
└── tasks.md                             # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
rg-logic/src/main/java/vg/rg/
├── security/
│   ├── AuthorityChecker.java                     # MODIFIED — add ONE method:
│   │                                             #            hasAuthority(UniqueId, String)
│   ├── WorkspaceScope.java                       # NEW — record(workspaceUniqueId, ownerUniqueId)
│   ├── WorkspaceScopeResolver.java               # NEW — interface: resolve(id, permission)
│   ├── WorkspaceScopeResolverImpl.java           # NEW — permission dispatch (package-private)
│   └── WorkspaceScopeProvider.java               # NEW — per-type seam: supports/byResource/byContainer
├── security/model/
│   ├── Permissions.java                          # MODIFIED — add Workspace.OWNER; MOVE OUT Location.*
│   └── LocalPermissions.java                     # NEW — Location.* + Workspace.* CRUD (8 constants)
├── config/
│   └── WorkspaceProperties.java                  # NEW — bounds (follows GeoProperties)
├── entity/
│   ├── WorkspaceEntity.java                      # NEW
│   ├── WorkspaceSelectionEntity.java             # NEW
│   ├── WorkspaceLocationEntity.java              # NEW — mandatory workspaceUniqueId = the containment
│   ├── WorkspaceMigrationMarkerEntity.java       # NEW — one-time migration marker
│   └── LocationEntity.java                       # DELETED — global scope retired
├── repository/
│   ├── WorkspaceRepository.java                  # NEW — also the resolver's step-1 lookup
│   ├── WorkspaceSelectionRepository.java         # NEW
│   ├── WorkspaceLocationRepository.java          # NEW — every query scoped by construction
│   ├── LegacyLocationRepository.java             # NEW — read-only migration source over the retained
│   │                                             #       rg_location; the only reader; goes when it drops
│   └── LocationRepository.java                   # DELETED
├── mapper/
│   ├── WorkspaceMapper.java                      # NEW — MapStruct
│   └── LocationMapper.java                       # MODIFIED — retargeted to WorkspaceLocationEntity
├── model/
│   └── WorkspaceModel.java                       # NEW  (LocationModel reused unchanged)
├── migration/
│   ├── GlobalLocationMigration.java              # NEW — interface
│   ├── GlobalLocationMigrationImpl.java          # NEW — idempotent, uses UniqueIdService
│   └── GlobalLocationMigrationRunner.java        # NEW — startup trigger, config-gated
└── service/
    ├── WorkspaceService.java / Impl              # NEW — lifecycle, ensureDefault
    ├── WorkspaceSelectionService.java / Impl     # NEW — active workspace
    ├── WorkspaceContentContributor.java          # NEW — removal extension seam
    ├── WorkspaceLocationService.java / Impl      # NEW — the only location service
    ├── LocationScopeProvider.java                # NEW — claims location:*; one join query to workspace
    ├── WorkspaceSelfScopeProvider.java           # NEW — claims workspace:*; a workspace scopes itself
    ├── LocationWorkspaceContentContributor.java  # NEW — the one contributor
    └── LocationService.java / Impl               # DELETED — global scope retired

rg-logic/src/main/resources/db/liquibase/
└── 002-workspace-init.yaml                       # NEW — 4 tables + a table comment marking
                                                  #       rg_location unused. NO dropTable anywhere.

rg-logic/src/test/java/vg/rg/
├── security/AuthorityCheckerTest.java            # MODIFIED — 4 enforced conditions; owner-without-
│                                                 #            capabilities allowed; permission-kind denial
├── security/WorkspaceScopeResolverFuncTest.java  # NEW — depth ≥ 5 via a test-only provider (1 query),
│                                                 #       location join, container path, fail-closed + warn
├── security/model/PermissionsTest.java           # MODIFIED — split declarations
├── security/model/LocalPermissionsTest.java      # NEW
├── security/PermissionDeclarationArchitectureTest.java # NEW — no local perms in app-wide declaration
├── migration/GlobalLocationMigrationFuncTest.java # NEW — A × L fan-out, idempotency, gating
└── service/                                       # NEW — Workspace*Test, *MethodSecurityTest, *FuncTest
                                                   #       DELETED — LocationService* tests

rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/
└── view/
    ├── MainView.java                             # MODIFIED — gated workspace item; locations item moves
    │                                             #            under it; old /locations item removed
    └── workspace/
        ├── WorkspaceLayout.java                  # NEW — @ParentLayout(MainView); hosts the selector
        ├── WorkspacesView.java                   # NEW — /workspaces
        ├── WorkspaceLocationsView.java           # NEW — /workspaces/locations (the only locations screen)
        └── LocationFormDialog.java               # MODIFIED — reused by the workspace-scoped view

# Removed: former `view/LocationsView.java` and its `/locations` route.

rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/
└── MapsResolutionBridge.java                     # MODIFIED — proximity call re-pointed at the workspace

rg-frontend-vaadin/src/main/resources/
├── messages.properties                           # MODIFIED — new keys (uk, default)
└── messages_en.properties                        # MODIFIED — new keys (en)
```

**Structure Decision**: the existing two-module layout is kept exactly as the constitution's
module-ownership rule requires — all business behaviour in `rg-logic` (`security`, `entity`, `repository`,
`mapper`, `model`, `service`, plus the Liquibase changelog), all presentation in `rg-frontend-vaadin`
(`MainView`, `view/*`, message bundles). No new module is introduced. The one deliberate structural
addition is the nested `WorkspaceLayout` router layout, which makes the selector's confinement a property
of the route tree rather than a rule repeated per view (research R5). A new `migration` package keeps the
one-time data migration out of the service layer, since it is startup-scoped rather than a business
operation and is deleted once the follow-up drop has shipped.

`WorkspaceScopeProvider` implementations live beside the type they describe (`LocationScopeProvider` and
`WorkspaceSelfScopeProvider` in `service`), not in `security`, so `security` owns only the seam interface
and the dispatch and never depends on a concrete type — which is what keeps adding a type from touching the
access rules. They sit in `service` rather than `repository` because they are `@Component`s with behaviour,
leaving `repository` purely declarative Spring Data interfaces as it is today.

Note the deletions: retiring the global scope removes `LocationEntity`, `LocationRepository`,
`LocationService`/`Impl`, `LocationsView`, and the `/locations` route. `LegacyLocationRepository` exists
only to read the retained source table during migration; it is the table's sole reader and is removed
together with it in the later change that drops it (research R10).

## Phase 0 — Outline & Research

**Status: complete** → [research.md](./research.md)

Resolved: how to resolve an identifier to its workspace (R1 — the declared permission names the type and
its verb names the lookup; one query per check, no index table, no probing, premised on the verified single
global id generator); the single check
and why ownership overrides the declared permission (R2 — one method on `AuthorityChecker`, four enforced
conditions, fail-closed, the permission formal but validated); traversal
bounds, failure modes, and caching (R3 — no depth bound needed, deny on failure, no cache yet); replacing the global store
with a mandatory-scope store (R4 — a new table, so the leakage class is removed from the schema rather
than tested for); confining the selector structurally (R5); guaranteeing one default workspace per owner
(R6 — nullable unique marker column, since MySQL lacks partial indexes); removing workspace contents
without hardcoding types (R7); proving depth-independence before deep types exist (R8 — test-only parent
resolvers forming a 5-level chain);
migrating the shared collection into per-author workspaces (R9 — full copy per author, application-level
because identifiers come from the central generator); what happens to the old store (R10 — retained and
marked unused, so the rollback is durable and nothing needs sequencing); and splitting local from app-wide
permission
declarations (R11 — each check accepts only its own kind, and workspace CRUD gains local permissions so a
single scoped check suffices).

No `NEEDS CLARIFICATION` markers remain.

## Phase 1 — Design & Contracts

**Status: complete**

- [data-model.md](./data-model.md) — `Workspace`, `WorkspaceSelection`, `WorkspaceLocation`, the migration
  marker, how containment and resolution work without an index table, the retired global entity, the two
  permission declarations, all bounds and their defaults, and the two-deployment schema plan.
- [contracts/authority-checker.md](./contracts/authority-checker.md) — the security boundary: the two
  checks and how each rejects the other's inputs, the four enforced conditions, why the fifth (holding the
  permission) is ignored for owners, the fail-closed rule, `WorkspaceScopeResolver` and its
  permission-dispatched resolution, the `WorkspaceScopeProvider` seam, call-site usage, and required
  coverage.
- [contracts/workspace-service.md](./contracts/workspace-service.md) — lifecycle, selection, and the
  content-contributor seam, with per-method guards and error signals.
- [contracts/workspace-location-service.md](./contracts/workspace-location-service.md) — the only location
  service, what is removed with the global scope, and the migration contract with its verification gate.
- [contracts/ui-navigation.md](./contracts/ui-navigation.md) — nav gating, the route tree, the selector's
  structural confinement, and presentation requirements.
- [quickstart.md](./quickstart.md) — commands and manual scenarios that verify each user story and success
  criterion.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `WorkspaceScopeProvider` seam (two implementations) | FR-035/FR-048 — each type needs to own its single-query path to the workspace, and `security` must not depend on a concrete domain type. | A containment-index table *(two designs ago)* added a second structure every write had to maintain, with a silent-unreachability failure mode. A probe loop *(the previous design)* needed no type hint but cost a query per type per level and required a depth bound. Hardcoding the type list in the resolver would make adding a type edit the security package. |
| New table `rg_workspace_selection` | FR-004 requires one active workspace per user, persisted server-side across sessions and devices. | Session or client storage loses the selection between devices and sessions, and client storage would let the client assert its own scope. A column on the user record is impossible — this application stores no user record. |
| New table `rg_workspace` | The container the whole feature is about. | None; it is the feature's primary entity. |
| Permission carries the dispatch key | The permission is already at every call site, so the type hint is free — and it buys one query per check at any depth, plus the removal of the depth bound and cycle handling entirely. | Probing needs no hint but costs a query per type per level. Passing the type as a separate argument could disagree with the permission. **Cost accepted**: permission and identifier must now describe the same resource; a mismatch denies (SC-023) rather than failing to compile. |
| The verb selects resource-vs-container lookup (FR-048) | Creation has no resource id, so `location:create` names the container. Without the verb rule, dispatching on the resource part alone would deny every creation. | Guarding creation with the container's own permission works but makes the guard stop describing the operation. Falling back to a workspace lookup on a miss reintroduces a probe and would silently accept mismatched pairs. A second method was explicitly removed earlier. |
| `WorkspaceContentContributor` interface with one implementation | FR-017 and SC-011 require a new contained type to join without changing workspace or access code, and SC-011 is measured as "0 changes to the access rules". | Calling the location repository directly from `WorkspaceService.delete` would make every future type edit workspace-removal code, failing SC-011. Database `ON DELETE CASCADE` on domain tables would bypass the business layer, so audit and validation would not run. |
| New table `rg_workspace_location` replacing `rg_location` | FR-018 requires the workspace reference to be mandatory, which an altered nullable column cannot express while the migration needs source and target to coexist. | Adding a nullable column to `rg_location` leaves a predicate every query must remember, forever. Backfilling in place destroys the source before verification, so there is no rollback window. |
| New table `rg_migration_marker` | FR-040/FR-041 require the migration to be idempotent and its completion to be checkable before anyone drops the source table. | Inferring completion from row counts alone cannot distinguish "not started" from "partially done", so a resumed run could duplicate copies. |
| One-time migration as application logic, not schema SQL | Every identifier comes from `UniqueIdService`; the migration needs fresh ones for A workspaces and A×L locations. Allocating them in raw SQL would bypass and risk corrupting the central allocator. | A Liquibase `customChange` reaching into Spring for beans is technically possible but makes schema migration depend on application wiring — fragile and hard to test. The ordering consequence that once forced a second deployment is gone, because nothing is dropped (research R10). |
| Retained dead table `rg_location` (+ `LegacyLocationRepository`) | It is the durable rollback for an otherwise irreversible data change, and it removes the need to sequence a drop against a migration that must run after Liquibase. | Dropping in the same changelog deletes the source before the migration runs. Dropping in a gated follow-up changeset works but spans two deployments and automates an irreversible step. **Cost accepted**: a dead table and its read-only repository linger until a later change; both are marked with a table comment naming the superseding table and the migration, so they do not become unexplained. |
| A × L row fan-out in the migration | The former collection was shared, so every migrated user could previously see every location. A narrower split would remove access they already had. | Assigning each location only to its author's workspace avoids duplication but silently drops read access to other authors' locations. **Costs accepted**: row count multiplies, and copies diverge once edited. |
| Migration in one uncapped transaction | FR-047 — all-or-nothing makes a partially populated workspace structurally impossible, which is the property FR-041 needs, and removes checkpoint/resumption logic entirely. | Batching with per-author commits bounds lock duration and memory but reintroduces exactly the partial state FR-041 forbids. A hard total cap turns a large dataset into a blocked startup. **Costs accepted**: long transaction, growing undo log, and a startup that blocks for the copy's duration on a large dataset; `rg.workspace.migration.enabled=false` is the operator's escape hatch. |
| Second permission declaration (`LocalPermissions`) | FR-042/FR-043 — a location capability is meaningless without a resource, and enforcing that requires the two checks to validate against different sets. | A single declaration with a naming convention cannot be enforced by a check, so the mis-scoping mistake stays possible. |
| `LocalPermissions.Workspace` CRUD declared but not yet enforced | FR-042/FR-045 — giving workspace operations *local* permissions is what lets one scoped check cover the workspace and its contents, removing the need for a second authority method. | A third `hasWorkspaceAuthority(UniqueId)` method *(the previous design)* worked but meant two overlapping checks and an invitation to guard with the wrong one. Special-casing the scoped check to accept `workspace:owner` reopens the mis-scoping hole FR-043 closes. |
| Declared-but-not-held permissions throughout | FR-015 — the permission is the seam for granular non-owner access; passing it now means that arrives without touching a call site. Ownership already grants complete authority (research R2). | Dropping the parameter would force revisiting every guard later. Enforcing it for owners is ceremony without a security benefit in a strictly single-owner workspace. **Condition on this**: introducing sharing must enable the held-check for non-owners *before* any non-owner can reach a workspace. |

## Notes for `/speckit-tasks`

1. **Foundational order matters**: the permission split, `WorkspaceProperties`, changelog 002, the
   entities/repositories, and `WorkspaceScopeResolver` + the one new `AuthorityChecker` method must land before
   any service that guards on them, because every later guard depends on that method existing.
2. **The security boundary is the highest-risk code.** Sequence its tests first — the depth ≥ 5 chain via
   test-only parent resolvers, every fail-closed case with its warn assertion, the owner-holding-no-
   capabilities case, and both permission-kind rejections — so no service is built on an unverified check.
   Note the resolver is now a permission dispatch plus one query per provider, so it is straightforward to
   unit-test; keep the integration test for the real location join, the container path, and the depth chain.
3. **The permission split touches existing call sites.** Moving `Location.*` out of `Permissions` breaks
   every current reference (`LocationServiceImpl`, `LocationsView`, `MainView`, `PermissionsTest`). Most of
   those files are being deleted anyway, so sequence the split *with* the deletions rather than before
   them, or the tree will not compile in between.
4. **The migration is the highest-risk change overall.** Give it its own phase: write the migration, its
   integration test (A × L fan-out, idempotency, **all-or-nothing rollback on injected failure**, and the
   disabled-toggle no-op), and the verification figures **before** deleting anything from the old location
   code, so the source of truth is proven copied before it stops being read. Note it is *not* irreversible
   here — the source table is retained (research R10) — so the risk is a bad copy, not lost data.
5. **Nothing drops `rg_location` in this feature.** Changelog 002 sets a table comment marking it unused
   and superseded; there is no `dropTable` anywhere (research R10). Do **not** generate a drop changeset —
   removal is a separate later change, made only after the migration is verified against real data. This
   also means the feature is a single self-contained deployment.
6. **Deletions are deliverables, not cleanup.** `LocationEntity`, `LocationRepository`,
   `LocationService`/`Impl`, `LocationsView`, and the `/locations` route must actually be removed for
   SC-019 to hold; leaving them behind "temporarily" keeps a reachable global scope alive.
7. **Final phase is mandatory** per the constitution: actualize `specs/current/` with a new `workspace.md`
   and **rewrite** `specs/current/geolocation.md`, whose "shared collection" framing is superseded — a
   location is now workspace-scoped, and the proximity suggestion is scoped to the active workspace.
