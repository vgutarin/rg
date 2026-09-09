# Tasks: Workspace Layer

**Input**: Design documents from `/specs/003-workspace-layer/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Test tasks are **mandatory** here, not optional. The project constitution requires that "all
executable application code MUST be covered by unit and integration tests" and that "every production-code
change MUST add or update both unit and integration tests", with contract/integration tests required
whenever an external-service or security boundary changes. This feature changes the authority boundary, so
its tests are sequenced *before* the services that depend on it.

**Organization**: Tasks are grouped by user story so each story can be implemented, tested, and demoed
independently. Two non-story phases carry work that belongs to no single story: **Phase 2** (the shared
security boundary) and **Phase 7** (retiring the global location scope).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: `[US1]`–`[US4]`, mapping to the user stories in [spec.md](./spec.md)
- Every task names its exact file path

## Path Conventions

Two-module JVM project, per [plan.md](./plan.md):

- Business logic: `rg-logic/src/main/java/vg/rg/…`, tests in `rg-logic/src/test/java/vg/rg/…`
- UI: `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/…`, tests under `rg-frontend-vaadin/src/test/java/…`
- Schema: `rg-logic/src/main/resources/db/liquibase/`
- Message bundles: `rg-frontend-vaadin/src/main/resources/messages.properties` (Ukrainian, default) and
  `messages_en.properties`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration scaffolding the rest of the feature reads. The project already exists, so this
phase is deliberately small.

- [X] T001 [P] Create `WorkspaceProperties` in `rg-logic/src/main/java/vg/rg/config/WorkspaceProperties.java` with `maxPerUser` (default 20), `nameMaxLength` (128), `descriptionMaxLength` (1024), and `migrationEnabled` (true), following the existing `GeoProperties` pattern. There is deliberately **no** ancestry-depth bound — resolution performs no runtime traversal (research R1, R3)
- [X] T002 [P] Add `rg.workspace.*` defaults for the properties above to `rg-frontend-vaadin/src/main/resources/application.properties`
- [X] T003 [P] Add `WorkspacePropertiesTest` in `rg-logic/src/test/java/vg/rg/config/WorkspacePropertiesTest.java` asserting defaults and rejection of non-positive bounds

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The permission declarations, schema, entities, repositories, and — most importantly — the
**authority boundary**. Every user story guards on the boundary, so nothing else may start until it is
implemented *and* proven.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

**⚠️ Compile-order note**: T004 keeps `Permissions.Location.*` in place temporarily so the existing
`LocationServiceImpl`, `LocationsView`, and `MainView` still compile. Those constants are removed in
**Phase 7**, together with the code that references them. Do not remove them earlier — the tree will not
build in between.

### Permission declarations

- [X] T004 Add nested `Workspace` class with `OWNER = "workspace:owner"` to `rg-logic/src/main/java/vg/rg/security/model/Permissions.java`, add it to `ALL`, and **temporarily retain** the existing `Location.*` constants (removed in Phase 7)
- [X] T005 [P] Create `LocalPermissions` in `rg-logic/src/main/java/vg/rg/security/model/LocalPermissions.java` declaring `Location.READ/CREATE/UPDATE/DELETE` and `Workspace.CREATE/READ/UPDATE/DELETE`, each nested group exposing its own `ALL` plus **`contains(String)`** (the dispatch key used by `WorkspaceScopeProvider.supports`), plus a top-level `ALL` and `isRecognized`, reusing `Permissions.hasValidFormat` so both declarations share one syntax rule (FR-042, FR-044, FR-035)
- [X] T006 Make `Permissions.recognized(Collection)` filter against the **union** of the app-wide and local declarations in `rg-logic/src/main/java/vg/rg/security/model/Permissions.java`, while keeping `Permissions.isRecognized` app-wide-only (research R11) — without the union, `location:read` would be silently dropped from a principal
- [X] T007 [P] Create `LocalPermissionsTest` in `rg-logic/src/test/java/vg/rg/security/model/LocalPermissionsTest.java` covering format validation, duplicate rejection, `isRecognized` returning false for app-wide permissions, and the exact declared set
- [X] T008 Update `rg-logic/src/test/java/vg/rg/security/model/PermissionsTest.java` for `Workspace.OWNER`, for `isRecognized` rejecting local permissions, and for `recognized(...)` accepting both kinds

### Schema

- [X] T009 Create `rg-logic/src/main/resources/db/liquibase/002-workspace-init.yaml` creating `rg_workspace` (nullable `name`, nullable-unique `default_for_owner`, `owner_unique_id` indexed, `version`, audit columns), `rg_workspace_selection` (`user_unique_id` PK), `rg_workspace_location` (**not-null** `workspace_unique_id` with FK, composite indexes `(workspace_unique_id, latitude)` and `(workspace_unique_id, name)`), and `rg_migration_marker`; plus a **table comment on `rg_location`** marking it unused and naming `rg_workspace_location` as its replacement. **No `dropTable` anywhere** (research R10, FR-037)

### Entities

- [X] T010 [P] Create `WorkspaceEntity` in `rg-logic/src/main/java/vg/rg/entity/WorkspaceEntity.java` — `@Version`, **nullable** `name` (null = system-named, FR-046), `ownerUniqueId`, `defaultForOwner`, JPA auditing via `AuditingEntityListener`, `UniqueIdLongConverter` on all id columns, following `LocationEntity`'s conventions
- [X] T011 [P] Create `WorkspaceSelectionEntity` in `rg-logic/src/main/java/vg/rg/entity/WorkspaceSelectionEntity.java` with `userUniqueId` as `@Id` and a non-null `workspaceUniqueId`
- [X] T012 [P] Create `WorkspaceLocationEntity` in `rg-logic/src/main/java/vg/rg/entity/WorkspaceLocationEntity.java` mirroring `LocationEntity`'s business columns plus a **non-null, immutable** `workspaceUniqueId`
- [X] T013 [P] Create `WorkspaceMigrationMarkerEntity` in `rg-logic/src/main/java/vg/rg/entity/WorkspaceMigrationMarkerEntity.java` with `name` as `@Id` and `completedAt`

### Repositories

- [X] T014 [P] Create `WorkspaceRepository` in `rg-logic/src/main/java/vg/rg/repository/WorkspaceRepository.java` extending `UniqueIdJpaRepository<WorkspaceEntity>` with `findByOwnerUniqueId`, `findByDefaultForOwner`, and `countByOwnerUniqueId`
- [X] T015 [P] Create `WorkspaceSelectionRepository` in `rg-logic/src/main/java/vg/rg/repository/WorkspaceSelectionRepository.java`
- [X] T016 [P] Create `WorkspaceLocationRepository` in `rg-logic/src/main/java/vg/rg/repository/WorkspaceLocationRepository.java` with the workspace-scoped query surface from [contracts/workspace-location-service.md](./contracts/workspace-location-service.md) — every method takes a workspace id, so no query can be written unscoped
- [X] T017 [P] Create `WorkspaceMigrationMarkerRepository` in `rg-logic/src/main/java/vg/rg/repository/WorkspaceMigrationMarkerRepository.java`
- [X] T018 [P] Create read-only `LegacyLocationRepository` in `rg-logic/src/main/java/vg/rg/repository/LegacyLocationRepository.java` over the retained `rg_location`, exposing only distinct-authors and paged read queries for the migration — its sole reader (research R10)

### The authority boundary (highest-risk code in the feature)

- [X] T019 [P] Create `WorkspaceScopeProvider` interface in `rg-logic/src/main/java/vg/rg/security/WorkspaceScopeProvider.java` with `boolean supports(String permission)`, `Optional<WorkspaceScope> findByResource(UniqueId)`, and `Optional<WorkspaceScope> findByContainer(UniqueId)` — the per-type extension seam (FR-017, FR-048, SC-011)
- [X] T020 [P] Create the `WorkspaceScope(UniqueId workspaceUniqueId, UniqueId ownerUniqueId)` record in `rg-logic/src/main/java/vg/rg/security/WorkspaceScope.java` and the `WorkspaceScopeResolver` interface (`Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission)`) in `rg-logic/src/main/java/vg/rg/security/WorkspaceScopeResolver.java`
- [X] T021 Implement `WorkspaceScopeResolverImpl` (package-private) in `rg-logic/src/main/java/vg/rg/security/WorkspaceScopeResolverImpl.java`: select the injected `WorkspaceScopeProvider` whose `supports(permission)` is true, then call `findByContainer` when the permission's verb is `create` and `findByResource` otherwise (FR-048); return empty for a null id, a permission no provider claims, or a lookup miss; **warn-log** with identifiers and the declared permission only — never a principal, name, or content (FR-035, FR-036, Principle I)
- [X] T022 Create the two providers: `WorkspaceSelfScopeProvider` in `rg-logic/src/main/java/vg/rg/service/WorkspaceSelfScopeProvider.java` (claims `workspace:*`; a workspace scopes itself; `findByContainer` unsupported) and `LocationScopeProvider` in `rg-logic/src/main/java/vg/rg/service/LocationScopeProvider.java` (claims `location:*` via `LocalPermissions.Location.contains`; `findByResource` runs **one** join query to `rg_workspace`, `findByContainer` reads the workspace by id)
- [X] T023 Add `hasAuthority(UniqueId resourceId, String permission)` to `rg-logic/src/main/java/vg/rg/security/AuthorityChecker.java`, injecting `WorkspaceScopeResolver`: require a **declared local** permission, an authenticated principal, the app-wide `workspace:owner`, and ownership of the resolved workspace — then permit **without** checking whether the permission is held (FR-013, FR-014, FR-015). Also make the existing flat `hasAuthority(String)` **reject local permissions** (FR-043)

### Boundary tests — write and run these before any service guards on them

- [X] T024 Create/extend `rg-logic/src/test/java/vg/rg/security/AuthorityCheckerTest.java` (`MockitoExtension`, mocked `WorkspaceScopeResolver`): each of the four enforced conditions denies in isolation and all four together allow; an **owner holding no local capabilities is allowed** (SC-006); a null resource id denies; an undeclared or app-wide permission denies **even for an owner** (SC-020); a local permission passed to the flat overload denies; the flat overload otherwise unchanged
- [X] T025 Create `rg-logic/src/test/java/vg/rg/security/WorkspaceScopeResolverFuncTest.java` extending `BaseFuncTest`: register a **test-only** `WorkspaceScopeProvider` claiming a test permission and resolving a workspace → group → event → comments → comment chain in one query, then assert the owner is allowed passing **any** identifier in it, a non-owner is denied for every one, and each check issues exactly **one** query (SC-015); a workspace id with `workspace:update` resolves to itself; a real location id with `location:update` resolves through the join; a workspace id with `location:create` resolves via `findByContainer` while the same id with `location:update` **denies** (FR-048, SC-023); and an unknown id, a permission no provider claims, and a row that does not join to a workspace each deny **and emit a warning** (SC-016)
- [X] T026 [P] Create `rg-logic/src/test/java/vg/rg/entity/WorkspaceSchemaFuncTest.java` extending `BaseFuncTest` asserting changelog 002 applies and its constraints hold: two defaults for one owner are rejected, a second non-default workspace is accepted, `rg_workspace_location.workspace_unique_id` rejects null, and `rg_location` still exists untouched

- [X] T026a [P] Create `PermissionSyntax` in `rg-logic/src/main/java/vg/rg/security/model/PermissionSyntax.java` holding the shared syntax rule, breaking the static-initialization cycle that arises once `Permissions` references `LocalPermissions` to build its recognized union
- [X] T026b [P] Create `WorkspaceScopeRow` interface projection in `rg-logic/src/main/java/vg/rg/repository/WorkspaceScopeRow.java` and add `WorkspaceScopeResolverTest` in `rg-logic/src/test/java/vg/rg/security/WorkspaceScopeResolverTest.java` for the dispatch rules and the depth-≥5 proof (a synthetic provider cannot be registered in the Spring context without colliding with the production ones)

**Checkpoint**: the authority boundary is implemented and proven at depth. User story work can begin.

---

## Phase 3: User Story 1 — Work inside a workspace (Priority: P1) 🎯 MVP

**Goal**: A `workspace:owner` holder sees the workspace navigation item, enters the section, finds a default
workspace already provisioned and active with a localized label, and creates a location inside it that
appears only there.

**Independent Test**: Sign in as a permission-holding user who has never used the application; confirm the
nav item is present, a default workspace exists and is active, a location created inside it is listed
there, and it is absent from a second workspace.

### Business logic

- [X] T027 [P] [US1] Create `WorkspaceModel` in `rg-logic/src/main/java/vg/rg/model/WorkspaceModel.java` — `uniqueId`, `version`, **nullable** `name` (null = system-named), `description`, read-only `defaultWorkspace`; owner/author/lastEditor deliberately **not** exposed (FR-022, FR-046)
- [X] T028 [P] [US1] Create `WorkspaceMapper` (MapStruct) in `rg-logic/src/main/java/vg/rg/mapper/WorkspaceMapper.java`
- [X] T029 [US1] Create `WorkspaceService` interface in `rg-logic/src/main/java/vg/rg/service/WorkspaceService.java` per [contracts/workspace-service.md](./contracts/workspace-service.md)
- [X] T030 [US1] Implement `create`, `listOwned`, `find`, and `ensureDefault` in package-private `rg-logic/src/main/java/vg/rg/service/WorkspaceServiceImpl.java`: `create` requires a non-blank bounded name and enforces `maxPerUser`; `ensureDefault` creates the default with **`name = null`** and recovers from the unique-index race by re-reading rather than surfacing the violation (FR-002, FR-046, research R6). Guards: flat `workspace:owner` for `create`/`listOwned`/`ensureDefault`, `hasAuthority(#workspaceId, LocalPermissions.Workspace.READ)` for `find`
- [X] T031 [US1] Create `WorkspaceSelectionService` interface in `rg-logic/src/main/java/vg/rg/service/WorkspaceSelectionService.java` and implement `activeWorkspace()` in `rg-logic/src/main/java/vg/rg/service/WorkspaceSelectionServiceImpl.java`, falling back to `ensureDefault()` when the selection is absent, dangling, or no longer owned, and repairing the stored row (FR-004)
- [X] T032 [US1] Create `WorkspaceLocationService` interface in `rg-logic/src/main/java/vg/rg/service/WorkspaceLocationService.java` per [contracts/workspace-location-service.md](./contracts/workspace-location-service.md)
- [X] T033 [US1] Implement `WorkspaceLocationServiceImpl` in `rg-logic/src/main/java/vg/rg/service/WorkspaceLocationServiceImpl.java` — `create`/`update`/`delete`/`browse`/`searchByName`/`findNearby`, reusing `GeoDistance` and `GeoProperties` unchanged. Guards pass the **resource's own** identifier for `update`/`delete` and the workspace for `create`/reads (FR-045)
- [X] T034 [US1] Retarget `rg-logic/src/main/java/vg/rg/mapper/LocationMapper.java` to `WorkspaceLocationEntity`, keeping `LocationModel` unchanged and **not** exposing `workspaceUniqueId` on the model (FR-016)

### Business-logic tests

- [X] T035 [P] [US1] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceServiceImplTest.java` (`MockitoExtension`): name/description validation, `maxPerUser` refusal, `ensureDefault` creating with a null name, `ensureDefault` idempotent, and the unique-index race recovery
- [X] T036 [P] [US1] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceLocationServiceImplTest.java` (`MockitoExtension`): validation and mapping per method, `create` setting only the given workspace, and `update` unable to change the workspace
- [X] T037 [P] [US1] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceServiceMethodSecurityTest.java` following the `LocationServiceMethodSecurityTest` pattern (`AnnotationConfigApplicationContext` + `@EnableMethodSecurity`): every method denies without authentication and without `workspace:owner`; `find` denies for a workspace owned by another user
- [X] T038 [P] [US1] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceLocationServiceMethodSecurityTest.java`: each method denies without authentication, without `workspace:owner`, and when the resolved workspace belongs to another user; **allows an owner holding no `location:*` permission** (SC-006); denies an app-wide or undeclared permission (SC-020)
- [X] T039 [US1] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceLocationServiceFuncTest.java` extending `BaseFuncTest`: create a location in a workspace, confirm it is returned by that workspace's `browse` and absent from a second workspace's, and confirm `update`/`delete` resolve correctly when passed the **location's** identifier
- [X] T040 [P] [US1] Create `rg-logic/src/test/java/vg/rg/service/LocationScopeProviderTest.java`: `supports` claims only `location:*`; `findByResource` returns the row's workspace and owner for a known id and empty for an unknown one; `findByContainer` returns the workspace by id

### UI

- [X] T041 [US1] Create `WorkspaceLayout` in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayout.java` — `@ParentLayout(MainView.class)`, `implements RouterLayout`, `@PermitAll` plus `BeforeEnterObserver` (the `LocationsView` pattern); resolves the active workspace once per navigation via `WorkspaceSelectionService.activeWorkspace()`; renders the **active-workspace selector**, showing a localized `workspace.default.name` label when the model's `name` is null and the stored text otherwise (FR-005, FR-032, FR-046)
- [X] T042 [US1] Create `WorkspacesView` in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspacesView.java` at `@Route(value = "workspaces", layout = WorkspaceLayout.class)` — list owned workspaces and create one with a bounded name and optional description, mobile-first with loading/empty/error/retry states
- [X] T043 [US1] Create `WorkspaceLocationsView` in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLocationsView.java` at `@Route(value = "workspaces/locations", layout = WorkspaceLayout.class)`, carrying over `LocationsView`'s two-tab structure, `LocationFormDialog`, and the Google Maps picker, but calling `WorkspaceLocationService` with the active workspace; render an explicit localized **denial** (not an empty list) when the user lacks `location:read`
- [X] T044 [US1] Add the permission-gated workspace navigation item to `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/MainView.java`, with a child locations entry gated additionally on `LocalPermissions.Location.READ`; leave the existing top-level `/locations` item in place until Phase 7 (FR-030)
- [X] T045 [US1] Add all new message keys — nav item, page titles, selector label, `workspace.default.name`, create action and validation, denial, and the free-text personal-data guidance — to **both** `rg-frontend-vaadin/src/main/resources/messages.properties` (Ukrainian, default) and `messages_en.properties` (FR-024, FR-025)

### UI tests

- [X] T046 [P] [US1] Update `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/MainViewTest.java`: the workspace nav item is present with `workspace:owner`, and the child locations entry appears only when `location:read` is also held
- [X] T047 [P] [US1] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayoutTest.java`: the selector renders the localized label for a system-named workspace and the stored text once named (SC-021, FR-046), and the active workspace is named on entry
- [X] T048 [P] [US1] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLocationsViewTest.java`: a user holding `workspace:owner` without `location:read` sees a denial rather than an empty list

- [X] T040a [P] [US1] Add `WorkspaceLocationMapper` in `rg-logic/src/main/java/vg/rg/mapper/WorkspaceLocationMapper.java` rather than retargeting `LocationMapper` (T034), because the still-live global `LocationServiceImpl` maps the retired entity — the two merge in Phase 7
- [X] T040b [P] [US1] Add `LocalPermissions.Location.LIST` and classify it container-addressed: `browse`/`searchByName`/`findNearby` pass the **workspace** identifier, so guarding them with the resource-addressed `location:read` resolved nothing and denied every collection read
- [X] T040c [P] [US1] Enable `@EnableMethodSecurity` in `WorkspaceLocationServiceFuncTest` via a nested `@TestConfiguration` — `BaseFuncTest` does not enable it (the application's lives in the UI module), so every "is denied" assertion in a func test would otherwise pass vacuously

- [X] T048a [P] [US1] Add a workspace-scoped `resolveAndSuggest(workspaceId, ...)` overload to `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/MapsResolutionBridge.java` so the proximity suggestion is confined to the active workspace; the global overload stays until the global scope is retired (T078)
- [X] T048b [P] [US1] `MainView.addNav` now returns the created `SideNavItem` and a new `addChildNav` nests the section's screens under it, so the workspace locations entry sits inside the section rather than beside it

**Checkpoint**: User Story 1 is fully functional. A permission holder can enter the section and keep
locations in a workspace. `/locations` still works — it is retired in Phase 7.

---

## Phase 4: User Story 2 — Keep workspaces isolated and switch between them (Priority: P2)

**Goal**: Creating a second workspace and switching to it replaces all visible workspace content in one
action, with nothing carried over, and the selection survives the session.

**Independent Test**: Create two workspaces with different content, switch between them, confirm each view
shows exactly one workspace's content, a search in one never returns the other's, and the selection
survives signing out and back in.

- [X] T049 [US2] Implement `select(UniqueId)` in `rg-logic/src/main/java/vg/rg/service/WorkspaceSelectionServiceImpl.java`, guarded by `hasAuthority(#workspaceId, LocalPermissions.Workspace.READ)`, replacing the caller's single selection row (FR-004)
- [X] T050 [US2] Wire the selector's value-change handler in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayout.java` to call `select(...)` and refresh the child view in **one** user action (SC-003)
- [X] T051 [P] [US2] Add selector and switching message keys to both bundles in `rg-frontend-vaadin/src/main/resources/`
- [X] T052 [P] [US2] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceSelectionServiceImplTest.java` (`MockitoExtension`): `select` rejects a workspace the caller does not own; `activeWorkspace` falls back to the default when the selection is absent, dangling, or no longer owned, and repairs the row
- [X] T053 [US2] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceIsolationFuncTest.java` extending `BaseFuncTest` — the **zero-leakage** test (SC-001): across `browse`, `searchByName`, and `findNearby`, every returned row belongs to the workspace in context and none to another; requesting a location from workspace A while B is active is denied without disclosing existence
- [X] T054 [P] [US2] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceSelectionFuncTest.java`: the selection persists across sessions for the same user and is per user, not per device (FR-004)
- [X] T055 [P] [US2] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceSwitchTest.java`: one switch action replaces the child view's content wholly, with no item carried over

- [X] T055a [P] [US2] Make the switch testable: `WorkspaceLayout.selectWorkspace` re-resolves the active workspace from the service and re-renders, with the UI refresh isolated behind a null-safe helper — the previous `UI.getCurrent().getPage().reload()` threw without a UI, so the switch could not be asserted at all

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 — Access gated by permission, granted by ownership (Priority: P3)

**Goal**: The layer is invisible and unreachable without `workspace:owner`; within it, ownership grants
complete authority; revoking the permission withdraws access without deleting anything.

**Independent Test**: Attempt every workspace operation as a user without `workspace:owner` — including by
navigating straight to a workspace route — and confirm each is denied with no nav item shown and no
workspace provisioned; then confirm an owner succeeds on every operation while holding none of the
individual capabilities.

- [X] T056 [US3] Enforce the gate in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayout.java`: `beforeEnter` reroutes a user lacking `workspace:owner` to the existing `NoAccessView` and provisions **no** workspace for them (FR-031, SC-012)
- [X] T057 [P] [US3] **Resolved by adding no key.** An unentitled caller is rerouted to the existing generic no-access / access-denied views, which already carry localized text. A workspace-specific denial message would disclose that the section exists — exactly what FR-030 forbids — so the speculative `workspace.access.denied` key was removed from both bundles and the reasoning recorded there as a comment (FR-023, FR-030)
- [X] T058 [P] [US3] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceAccessGateTest.java`: no nav item without the permission, direct navigation to `/workspaces` reroutes, and no workspace is provisioned for such a user (SC-012)
- [X] T059 [US3] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceOwnershipAuthorityFuncTest.java` extending `BaseFuncTest`: a non-owner is denied on **every** operation against another user's workspace and its contents (SC-005); an owner holding `workspace:owner` and **no** local capabilities succeeds on every operation (SC-006); no denial reveals whether the target exists
- [X] T060 [P] [US3] Create `rg-logic/src/test/java/vg/rg/service/WorkspacePermissionRevocationFuncTest.java`: revoking `workspace:owner` denies every operation while deleting **0** workspaces and **0** contained objects, and re-granting restores access to all of it (FR-033, SC-013)
- [X] T061 [P] [US3] Update `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/reports/PermissionAwareViewsTest.java` for the workspace routes and the relocated locations entry

- [X] T061a [P] [US3] Add `WorkspaceAccessGateTest` in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceAccessGateTest.java` covering each **route's own** gate — the layout's was already tested, but `WorkspacesView` and `WorkspaceLocationsView` each enforce independently, and each must provision nothing when denying

**Checkpoint**: All access rules are enforced and proven at both the service and UI layers.

---

## Phase 6: User Story 4 — Manage the workspace lifecycle (Priority: P4)

**Goal**: Rename a workspace (including naming a system-named one), remove one behind a confirmation that
states its contents go with it, never end up with no workspace, and reject stale saves.

**Independent Test**: Create a workspace, rename it, add content, delete it with confirmation, and verify
its content is gone, the default workspace survives, another becomes active, and a second workspace's
content is unchanged.

- [X] T062 [P] [US4] Create `WorkspaceContentContributor` interface in `rg-logic/src/main/java/vg/rg/service/WorkspaceContentContributor.java` with `resourceType()` and `deleteAllInWorkspace(UniqueId)` — the removal extension seam (FR-017, SC-011)
- [X] T063 [P] [US4] Create `LocationWorkspaceContentContributor` in `rg-logic/src/main/java/vg/rg/service/LocationWorkspaceContentContributor.java` delegating to `deleteByWorkspaceUniqueId`, touching no other workspace's rows
- [X] T064 [US4] Implement `update` and `delete` in `rg-logic/src/main/java/vg/rg/service/WorkspaceServiceImpl.java`: `update` requires a non-blank name (so this is also how a system-named workspace gets one, FR-046) and rejects a stale `version` with `ObjectOptimisticLockingFailureException`; `delete` refuses when `defaultForOwner` is set, otherwise runs every contributor then removes the workspace in one transaction and repoints any selection to the default (FR-006 – FR-008). Guards use `LocalPermissions.Workspace.UPDATE` / `.DELETE`
- [X] T065 [US4] Add rename and remove to `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspacesView.java` — a confirmation dialog stating the contained objects will be removed, guarded against double submission, plus the default-workspace refusal, the workspace-limit refusal, and the "reload and retry" stale-save guidance (FR-007 – FR-009, FR-026)
- [X] T066 [P] [US4] Add rename/remove/confirmation/refusal message keys to both bundles in `rg-frontend-vaadin/src/main/resources/`
- [X] T067 [P] [US4] Extend `rg-logic/src/test/java/vg/rg/service/WorkspaceServiceImplTest.java` for `update` (including naming a system-named workspace), stale-version rejection, the default-workspace delete refusal, and contributor invocation on delete
- [X] T068 [US4] Create `rg-logic/src/test/java/vg/rg/service/WorkspaceDeletionFuncTest.java` extending `BaseFuncTest`: deleting a workspace removes **100%** of its locations and changes another workspace's count by **0** (SC-004); the active selection repoints to the default; and across every removal path the number of permission-holding users left with zero workspaces is **0** (SC-007)
- [X] T069 [P] [US4] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspacesViewTest.java`: the confirmation step blocks removal until confirmed, and the limit/default/stale refusals render localized

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Retire the Global Location Scope

**Purpose**: Migrate the existing shared collection into per-author workspaces, then remove the global
service, view, and route. Belongs to no single user story — it is what FR-019 and FR-037 – FR-047 require.

**⚠️ Order matters**: the migration and its tests come **first**. Nothing from the old location code is
deleted until the data is proven copied. The risk here is a bad copy, not lost data — `rg_location` is
retained (research R10).

### Migration

- [X] T070 [P] Create `GlobalLocationMigration` interface and the `MigrationOutcome(performed, workspacesCreated, locationsCopied)` record in `rg-logic/src/main/java/vg/rg/migration/GlobalLocationMigration.java`
- [X] T071 Implement `GlobalLocationMigrationImpl` in `rg-logic/src/main/java/vg/rg/migration/GlobalLocationMigrationImpl.java` as a **single `@Transactional` unit** (FR-047): declare its own marker name (`GLOBAL_LOCATION_TO_WORKSPACE`) — the constant is deliberately not pre-declared on the entity, so the name lives with the code that writes it; stop if the marker is set; read distinct authors from `LegacyLocationRepository`; create one default workspace per author with `name = null` (skipping an author who already has one); copy **every** source location into **each** of those workspaces with a fresh `UniqueIdService` identifier, `workspaceUniqueId` set, and the source's content and original `author` preserved; write the marker last. No batching, no checkpointing, no cap (FR-038 – FR-040)
- [X] T072 Create `GlobalLocationMigrationRunner` in `rg-logic/src/main/java/vg/rg/migration/GlobalLocationMigrationRunner.java` invoking the migration once at startup, gated on `rg.workspace.migration.enabled`, and logging the `MigrationOutcome` so the A × L result can be verified against `rg_location` in the deployed environment
- [X] T073 Create `rg-logic/src/test/java/vg/rg/migration/GlobalLocationMigrationFuncTest.java` extending `BaseFuncTest`: seed `rg_location` with L rows across A distinct authors and assert exactly A default workspaces each holding exactly L locations — **A × L** in total — with content and author attribution preserved (SC-017); a second run adds **0** workspaces and **0** copies (SC-018); an author with a pre-existing default workspace gets no second one and has the copies placed into the existing one; an **injected failure mid-run rolls everything back** — 0 workspaces, 0 copies, no marker (SC-022); the source rows are unmodified after both a failed and a successful run (FR-041); and `migrationEnabled=false` performs nothing

### Removals

- [X] T074 Delete `LocationService`, `LocationServiceImpl`, `LocationEntity`, and `LocationRepository` from `rg-logic/src/main/java/vg/rg/` (`service/`, `entity/`, `repository/`) — the global scope is retired (FR-019)
- [X] T075 Delete the now-obsolete global tests `rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java`, `LocationServiceFuncTest.java`, and `LocationServiceMethodSecurityTest.java`, whose behaviour is covered by their workspace-scoped counterparts
- [X] T076 Remove the temporarily retained `Location.*` constants from `rg-logic/src/main/java/vg/rg/security/model/Permissions.java`, completing the split begun in T004 (FR-042)
- [X] T077 Delete `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java` and its `/locations` route, and remove the top-level locations nav item from `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/MainView.java`; delete `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/LocationsViewTest.java`
- [X] T078 Re-point the proximity call in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/MapsResolutionBridge.java` at the active workspace via `WorkspaceLocationService`, and update `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/MapsResolutionBridgeTest.java`

### Retirement verification

- [X] T079 [P] Create `rg-logic/src/test/java/vg/rg/security/PermissionDeclarationArchitectureTest.java` asserting no local permission (`location:*`, `workspace:` CRUD) is referenced from the app-wide declaration, and that no production call site passes a local permission to the flat `hasAuthority(String)` overload (research R11)
- [X] T080 [P] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/GlobalScopeRetiredTest.java` asserting `/locations` no longer resolves and that **0** navigation entries or routes serve a global location collection (SC-019)
- [X] T081 [P] Add an assertion to `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationContractArchitectureTest.java` that no production code outside `vg.rg.migration` and `LegacyLocationRepository` references `rg_location` (SC-019)

**Deviations, recorded rather than silently taken:**

- **T074 keeps `LocationEntity`.** It is the only JPA mapping of `rg_location`, and
  `LegacyLocationRepository` reads through it — the alternative, a second `@Entity` over the same table,
  invites ambiguity about which one is authoritative. Deleting it would mean deleting the retained
  rollback's only reader. It goes when the table does, in the later change.
- **T074 also removes `LocationMapper`**, which the task did not list: with `LocationServiceImpl` gone it
  had no remaining caller. The migration copies entity-to-entity and names each field it carries, because
  which fields survive a copy is exactly the auditable part.
- **T078 removed the unscoped `resolveAndSuggest` overload entirely** rather than re-pointing it. With the
  global screen gone its only caller went too, and leaving an unscoped overload behind is precisely the
  thing a caller reaches for by accident. `MapsResolutionBridge` now requires a workspace.
- **T081's assertion lives in the new `PermissionDeclarationArchitectureTest`**, not in
  `SecureAuthorizationContractArchitectureTest`: it is a retirement invariant, and it sits next to the
  other three. It states the rule (only `vg.rg.migration` plus the two read-only mapping files may name
  `rg_location`) rather than pinning a file list.
- **T080's test is in package `vg.rg.frontend.vaadin`**, not `...vaadin.view`: its substantive assertions
  read `MainView.navigationLabels()`, which is package-private. Widening production visibility to suit a
  test's location would be the wrong trade.
- **T076 required a one-line change to the uncommitted `TelegramAuthView` debug helper.** Its synthetic
  principal held `Permissions.Location.*`, which no longer exist; it now holds `Permissions.Workspace.OWNER`,
  which is what a debug session actually needs to reach the locations screens at all.
- **Bundle keys the retirement killed were removed here** rather than deferred to T082: `nav.locations`,
  `page.locations.title`, and the four `permission.location:*` labels. Four `location.*` keys were found
  dead *before* this phase (`location.selected`, `location.close`, `location.preview.unavailable`,
  `location.coordinates-selected`) and are left for T082, which owns bundle hygiene.
- **The migration is disabled in the functional-test profile.** `@SpringBootTest` invokes
  `ApplicationRunner` beans, so the startup migration would otherwise run — and write its completion
  marker — inside every functional test context before any test body executed. Its own test drives the
  migration directly and asserts the runner's gate separately.

**Checkpoint**: locations exist only inside workspaces; `rg_location` remains on disk, unused, as the
rollback.

---

## Phase 8: Polish, Cross-Cutting Concerns & Specification Actualization

**Purpose**: Quality gates spanning all stories, plus the actualization the constitution requires of every
task plan.

- [X] T082 [P] Update `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocalizationBundleTest.java` to assert every new key exists in **both** bundles with no missing-key fallback (FR-025)
- [X] T083 [P] Create `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceResponsiveLocaleTest.java` covering every workspace flow at the narrowest supported viewport and in the default plus one non-default locale, including a maximum-length workspace name and a system-named workspace's localized label (SC-009, SC-021)
- [X] T084 [P] Extend `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationObservabilityTest.java` to assert the workspace layer logs no personal data: `rg-logic/src/main/java/vg/rg/security/WorkspaceResolverImpl.java` warn logs carry only identifiers and depth, `rg-logic/src/main/java/vg/rg/migration/GlobalLocationMigrationRunner.java` logs only counts, and no new entity holds a personal-data field (SC-008, Principle I)
- [X] T085 Run the narrowest-first verification sequence from [quickstart.md](./quickstart.md), then `./gradlew test`, since the change spans both modules
- [ ] T086 Walk the manual scenarios in [quickstart.md](./quickstart.md) — A0 (migration), A, B, C, D, E, F — and record the verified A × L figure needed before the later change that drops `rg_location`
  - **Scenario A0: VERIFIED MANUALLY, 2026-09-03.** The maintainer confirmed against the deployed
    environment that the one-time migration completed. That check is what the migration's removal (Phase 9)
    and any future decision to drop `rg_location` rest on — it was made *before* the code was deleted and
    cannot be re-run afterwards, so this line is the record.
  - Scenarios A–F remain walkable and still need a Telegram Mini App session over https.
- [X] T087 Create `specs/current/workspace.md` describing the implemented workspace layer as current-state behaviour: the container and its lifecycle, the `workspace:owner` gate, the local/app-wide permission split, the single resource-addressed authority check with ownership override, probe-based workspace resolution, and the navigation section
- [X] T088 **Rewrite** `specs/current/geolocation.md`: its "shared collection" framing is superseded — a location is now workspace-scoped, the proximity suggestion is scoped to the active workspace, and the screen lives at `/workspaces/locations`. Cross-reference `specs/current/workspace.md` (constitution: Specification Artifacts, and the mandatory actualization phase)
- [X] T089 Document in `README.md` (beside the existing authorization-facade notes) that `rg_location` is retained and marked unused, that no code reads it, and that dropping it is a **separate later change** to be made only after T086's verification records the A × L figure (FR-037, research R10)

**Phase 8 notes:**

- **T083 found a real gap and fixed it**: eleven `workspace-*` class names were applied in Java with **no
  CSS rules at all**. Default block layout happens to be single-column, so nothing looked broken, but a
  maximum-length name had nothing wrapping it and the row actions had no tap-target sizing. Mobile-first
  rules were added to `styles.css`, and `everyWorkspaceRuleIsStyled` now fails if a class name is applied
  without a rule behind it.
- **T082 goes beyond key parity.** Parity says the two bundles agree with each other and nothing about
  whether they agree with the code. The added checks are: every literal lookup in production resolves in
  both bundles, every business outcome code is translated (those never appear at a lookup site, so
  nothing else would notice them going missing), and no `workspace*` key is dead. Production sources
  only — counting test sources would let a key stay alive because a negative assertion mentions it.
- **T082's first version had a hole, found at runtime, now closed.** `permission.workspace:owner` had no
  translation, so the landing view rendered the raw key where a capability label belonged — missing since
  the gate shipped in Phase 1. The checks only covered *literal* lookups, and that key is composed as
  `"permission." + permission`. Composed is not the same as unenumerable: the set is `Permissions.ALL`, so
  the family is now checked in both directions — every declared permission has a label, and no label
  outlives its permission. That second direction removed `permission.home:read`, a label for a permission
  that does not exist. Verified by deleting the key and watching the test fail.
- **T084's runner-log assertion lives in `GlobalLocationMigrationFuncTest`**, not in
  `SecureAuthorizationObservabilityTest`: `GlobalLocationMigrationRunner` is package-private, and
  widening production visibility to suit a test's location is the wrong trade. The resolver-warning and
  personal-data-field assertions are in the observability test as specified.
- **T084's "depth" is gone from the log assertion** because it is gone from the design: resolution is one
  permission-dispatched query with no traversal, so there is no depth to log. The warnings carry the
  resource identifier, the permission, and which lookup failed.

---

## Phase 9: Remove the One-Time Migration

Not planned in this task list — requested after Phase 8, and recorded here because it changes what earlier
phases delivered.

- [X] Delete `GlobalLocationMigration`, its implementation and its startup runner, plus the functional
  test that proved the copy. A migration that can only fire once per environment, and has fired, is a
  startup path that can never run again — worse than no path, because it reads as live.
- [X] Delete what existed only to serve it: `LegacyLocationRepository`, and `LocationEntity`, whose sole
  remaining purpose since T074 was to be that repository's read-only mapping of `rg_location`.
- [X] Delete `WorkspaceMigrationMarkerEntity` and its repository, and the `rg.workspace.migration.enabled`
  gate with its property, defaults and tests.

**`rg_migration_marker` the table was kept.** Not for a hypothetical future migration — that would be the
speculative generality this plan has avoided throughout — but because its row is *evidence*. Dropping
`rg_location` is still an open decision, T086's verification can no longer be re-run, and once the
migration code is gone that row is the only durable record that the copy ever happened. Destroying
evidence for an open decision is the one irreversible move available here; keeping two columns and one row
costs nothing. Changeset `003-retire-global-location-migration.yaml` writes that meaning into the schema
as a table comment, because the code that used to explain the row no longer exists.

**Both retained tables are now entirely unmapped**, and the retirement invariant was tightened to match:
`noProductionCodeReachesTheRetainedTableAtAll` replaces the earlier allow-list, since there is no longer
any legitimate reader to exempt.

**Precondition confirmed before deleting:** the maintainer verified manually on 2026-09-03 that the
migration had completed in the deployed environment (T086, Scenario A0). The removal would not have been
safe without that, since an environment that had not yet run it would silently never copy its rows.

**Known consequence, accepted:** an environment that never started the application while the migration
existed will not copy its old `rg_location` rows. Restoring that means recovering the code from the
commit that removed it.

**This unblocks the `rg_location` drop.** Its stated gate — confirming the copy ran — is now satisfied, so
removing the table is available as a separate change whenever wanted. `rg_migration_marker` should outlive
it: the row is the record of *why* the drop was safe.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: depends on Phase 1 — **blocks every user story**
- **Phase 3 – 6 (User Stories)**: all depend on Phase 2; may then proceed in parallel or in priority order
- **Phase 7 (Retire global scope)**: depends on Phase 3 (its `WorkspaceLocationService` and views are the
  replacements) — the migration tasks T070 – T073 depend only on Phase 2
- **Phase 8 (Polish & actualization)**: depends on every phase above

### Critical path

```text
T001–T003  →  T004–T008 (permissions)  →  T009 (schema)  →  T010–T018 (entities/repos)
           →  T019–T023 (authority boundary)  →  T024–T026 (boundary PROVEN)
           →  T027–T048 (US1 = MVP)
           →  T070–T073 (migration PROVEN)  →  T074–T081 (removals)
           →  T082–T089 (polish + actualization)
```

### Within each user story

- Models before mappers before services before views
- Service tests alongside their service; **boundary tests (T024 – T026) before any service guards on it**
- Method-security tests before the view that relies on the guard
- Story complete before moving to the next priority

### Notable ordering constraints

- **T004 before T076**: `Permissions.Location.*` is retained through Phases 2 – 6 so the existing
  `LocationServiceImpl` / `LocationsView` / `MainView` keep compiling; T076 removes it in the same phase as
  the code that used it. Removing it earlier breaks the build.
- **T073 before T074**: never delete the old location code until the migration test proves the copy.
- **T041 before T042, T043, T050, T056**: `WorkspaceLayout` hosts every workspace route and the selector.
- **T009 before T010 – T018**: entities and repositories need their tables.
- **T021 before T023**: the checker injects the resolver.
- **T019, T022 before T021**: the resolver dispatches across the registered providers.
- **T005 before T019/T022**: `supports(...)` is implemented with `LocalPermissions.<Group>.contains(...)`.
- **T062, T063 before T064**: `delete` iterates the contributors.
- **T064 must repoint the selection BEFORE deleting the workspace row.** `rg_workspace_selection` carries
  a foreign key to `rg_workspace`, so deleting a selected workspace fails with a constraint violation —
  the database enforces the ordering rather than trusting the service to get it right. Proven by
  `WorkspaceSelectionFuncTest.deletingASelectedWorkspace_isPreventedByTheDatabase`.

### Parallel Opportunities

- **T065 discovered two defects in the view flow, both fixed rather than tested around**: a re-render
  failure could not be allowed to make a committed rename look rejected (the refresh now runs after the
  write, outside the catch, and swallows its own failures), and disabling the confirm button is
  presentation only — a server-side single-submit flag is what actually prevents a second removal.
- **T069 note**: Vaadin holds the *current* UI behind a weak reference, so a test UI must be held in a
  field. Without it `dialog.open()` fails intermittently with "No currently active UI found".
- **Phase 1**: T001, T002, T003 all together
- **Phase 2**: T005/T007 with T004; all entities T010 – T013 together; all repositories T014 – T018
  together; T019/T020 together; T026 alongside T024/T025
- **US1**: T027/T028 together; the test set T035 – T038 and T040 together; T046 – T048 together
- **US2**: T051/T052/T054/T055 together
- **US3**: T057/T058/T060/T061 together
- **US4**: T062/T063 together; T066/T067/T069 together
- **Phase 7**: T079/T080/T081 together
- **Phase 8**: T082/T083/T084 together
- **Across stories**: once Phase 2 is done, US1 – US4 can be staffed in parallel; only Phase 7's removals
  serialize against US1

---

## Parallel Example: Phase 2 Foundational

```bash
# All four entities at once (different files, no interdependencies):
Task: "Create WorkspaceEntity in rg-logic/src/main/java/vg/rg/entity/WorkspaceEntity.java"
Task: "Create WorkspaceSelectionEntity in rg-logic/src/main/java/vg/rg/entity/WorkspaceSelectionEntity.java"
Task: "Create WorkspaceLocationEntity in rg-logic/src/main/java/vg/rg/entity/WorkspaceLocationEntity.java"
Task: "Create WorkspaceMigrationMarkerEntity in rg-logic/src/main/java/vg/rg/entity/WorkspaceMigrationMarkerEntity.java"

# Then all five repositories at once:
Task: "Create WorkspaceRepository in rg-logic/src/main/java/vg/rg/repository/WorkspaceRepository.java"
Task: "Create WorkspaceSelectionRepository in rg-logic/src/main/java/vg/rg/repository/WorkspaceSelectionRepository.java"
Task: "Create WorkspaceLocationRepository in rg-logic/src/main/java/vg/rg/repository/WorkspaceLocationRepository.java"
Task: "Create WorkspaceMigrationMarkerRepository in rg-logic/src/main/java/vg/rg/repository/WorkspaceMigrationMarkerRepository.java"
Task: "Create LegacyLocationRepository in rg-logic/src/main/java/vg/rg/repository/LegacyLocationRepository.java"
```

## Parallel Example: User Story 1 tests

```bash
Task: "WorkspaceServiceImplTest in rg-logic/src/test/java/vg/rg/service/WorkspaceServiceImplTest.java"
Task: "WorkspaceLocationServiceImplTest in rg-logic/src/test/java/vg/rg/service/WorkspaceLocationServiceImplTest.java"
Task: "WorkspaceServiceMethodSecurityTest in rg-logic/src/test/java/vg/rg/service/WorkspaceServiceMethodSecurityTest.java"
Task: "WorkspaceLocationServiceMethodSecurityTest in rg-logic/src/test/java/vg/rg/service/WorkspaceLocationServiceMethodSecurityTest.java"
Task: "LocationParentResolverTest in rg-logic/src/test/java/vg/rg/service/LocationParentResolverTest.java"
```

---

## Implementation Strategy

### MVP scope: Phases 1 – 3 (through User Story 1)

1. Phase 1 — Setup (T001 – T003)
2. Phase 2 — Foundational (T004 – T026). **Do not skip T024 – T026**: every later guard depends on an
   authority boundary that has been proven, including at depth.
3. Phase 3 — User Story 1 (T027 – T048)
4. **STOP and VALIDATE**: a permission holder enters the section, gets a default workspace with a localized
   label, and keeps locations in it. `/locations` still works, so nothing is taken away yet.
5. Demoable.

### Incremental delivery

1. Setup + Foundational → the boundary is proven
2. + US1 → **MVP**: workspaces hold locations
3. + US2 → isolation and switching are observable
4. + US3 → the gate and ownership rules are enforced end to end
5. + US4 → lifecycle management
6. + Phase 7 → the global scope is retired (the only phase that removes existing behaviour)
7. + Phase 8 → polish, and `specs/current/` reflects what was built

Each step adds value without breaking the previous one. Phase 7 is the single point where existing
behaviour changes for users, which is why it is last and why its migration is proven before its deletions.

### Parallel team strategy

1. Everyone on Setup + Foundational; treat T019 – T026 as one person's focused work, since it is the
   feature's security boundary
2. Then: Dev A on US1 (the largest), Dev B on US2 + US4, Dev C on US3 + the Phase 7 migration
3. Serialize Phase 7's removals after US1 lands

---

## Notes

- `[P]` = different files, no dependencies on incomplete tasks
- Tests here are **required by the constitution**, not optional; every production task has test coverage in
  the same phase
- Use `MockitoExtension` for mock initialization, and do not verify a method that was already explicitly
  stubbed unless the interaction itself is under test (project convention)
- Verify with the narrowest relevant Gradle task first, then broaden to `./gradlew test`
- Commit after each task or logical group; do not commit without an explicit instruction to do so
- The one-time migration is atomic: its only states are "not run" and "complete"
- Nothing in this plan drops `rg_location` — that is a separate later change (T089)
