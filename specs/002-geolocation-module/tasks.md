---

description: "Task list for Geolocation Module implementation"
---

# Tasks: Geolocation Module

**Input**: Design documents from `/specs/002-geolocation-module/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: Included — the project constitution (Development Workflow and Mandatory Test Coverage)
requires unit **and** integration tests for every production change; test tasks are therefore not
optional here.

**Organization**: Tasks are grouped by user story (US1–US6 from spec.md) for independent
implementation and testing.

**DB convention (per user instruction)**: Model every entity on `TemplateEntity` and every repository
on `TemplateRepository` — entities implement `UniqueIdEntity`, repositories extend
`UniqueIdJpaRepository<...>`, and creation uses `repository.saveWithNewUniqueId(entity, uniqueIdService)`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US6; Setup/Foundational/Polish have no story label
- File paths are repository-relative

## Path Conventions

Multi-module Gradle web app: `rg-logic/` (domain) and `rg-frontend-vaadin/` (Vaadin UI), per plan.md.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Permissions, configuration, and localization scaffolding used by all stories

- [X] T001 [P] Add `Location` permissions (`location:view`, `location:add`, `location:edit`, `location:delete`) as a nested class in `rg-logic/src/main/java/vg/rg/security/model/Permissions.java` and include them in `Permissions.ALL` (colon format, per contracts/permissions.md)
- [X] T002 [P] Create `GeoProperties` (`@ConfigurationProperties("rg.geo")`, `matchRadiusMeters` default 500, `maxNameSearchResults`) in `rg-logic/src/main/java/vg/rg/config/GeoProperties.java`
- [X] T003 [P] Add i18n keys for locations and permissions (`location.*`, `permission.location\:view|add|edit|delete`, validation/empty/no-results/conflict messages) to `rg-frontend-vaadin/src/main/resources/messages.properties` and `rg-frontend-vaadin/src/main/resources/messages_en.properties`
- [X] T004 [P] Add config placeholders (`google.maps.browser-api-key=${GOOGLE_MAPS_BROWSER_API_KEY:}`, `rg.geo.match-radius-meters`) to `rg-frontend-vaadin/src/main/resources/application.properties` (value from env; never commit the key)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data layer, mapper, auditing, and service skeleton that every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 Create `LocationEntity` in `rg-logic/src/main/java/vg/rg/entity/LocationEntity.java` modeled on `TemplateEntity` — `implements UniqueIdEntity`, `@Id Long uniqueId`, `@Version int version`, `@CreatedDate`/`@LastModifiedDate` (Instant), `@CreatedBy UniqueId author`, `@LastModifiedBy UniqueId lastEditor` (persisted via the library-provided autoApply `vg.unique.id.jpa.UniqueIdLongConverter` → BIGINT), `latitude`/`longitude` `BigDecimal` (DECIMAL(9,6)), `name`, `description`, `googlePlaceId`; Lombok + `@EntityListeners(AuditingEntityListener.class)` (see data-model.md)
- [X] T006 Create Liquibase changelog `rg-logic/src/main/resources/db/liquibase/001-location-init.yaml` for table `rg_location` (columns per T005, `pk_rg_location`, B-tree indexes on `latitude` and `name`), following `001-template-db-init.yaml` style (picked up by `includeAll`)
- [X] T007 [P] Create `LocationRepository extends UniqueIdJpaRepository<LocationEntity>` in `rg-logic/src/main/java/vg/rg/repository/LocationRepository.java` (like `TemplateRepository`) with a bounding-box `@Query` (`latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng`) and `findByNameContainingIgnoreCase(String, Pageable)`
- [X] T008 [P] Create domain models `LocationModel` (implements `Identifiable`), `ProximityQuery`, `ProximityMatch` in `rg-logic/src/main/java/vg/rg/model/` (fields per data-model.md)
- [X] T009 [P] Create `LocationMapper` (`@Mapper(componentModel="spring", uses=UniqueIdMapper.class)`) in `rg-logic/src/main/java/vg/rg/mapper/LocationMapper.java` mirroring `TemplateMapper`
- [X] T010 Create `CurrentUserAuditorAware implements AuditorAware<UniqueId>` (returns `AuthorityChecker.currentUserUniqueId()`) in `rg-logic/src/main/java/vg/rg/security/CurrentUserAuditorAware.java`, and set `@EnableJpaAuditing(auditorAwareRef = "currentUserAuditorAware")` in `rg-logic/src/main/java/vg/rg/RgLogicConfig.java`. (The `UniqueId`↔BIGINT persistence is handled by the **library-provided** autoApply `vg.unique.id.jpa.UniqueIdLongConverter` — do NOT declare a second converter; a duplicate autoApply converter fails context startup.)
- [X] T011 Create `LocationService` interface in `rg-logic/src/main/java/vg/rg/service/LocationService.java` with `create`, `update`, `delete`, `findNearby`, `searchByName`, `browse` (signatures per contracts/location-service.md)
- [X] T012 Create `LocationServiceImpl` skeleton in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java` — inject `UniqueIdService`, `LocationRepository`, `LocationMapper`, `GeoProperties`, `AuthorityChecker`; add `@PreAuthorize("@authorityChecker.hasAuthority('...')")` on each method (bodies filled per story), following `ProtectedActionServiceImpl`
- [X] T013 [P] Extend `rg-logic/src/test/java/vg/rg/security/model/PermissionsTest.java` to assert the four `location:*` permissions are recognized, well-formed, and present in `ALL`

**Checkpoint**: Data layer, auditing, permissions, and service contract are ready — stories can begin

---

## Phase 3: User Story 1 - Suggest existing nearby places from coordinates (Priority: P1) 🎯 MVP

**Goal**: Given coordinates, advisory-suggest saved locations within ±500 m nearest-first; never block adding a new one.

**Independent Test**: Seed a location; query ~100 m away → it is suggested; ~2 km away → not suggested; confirm "add new anyway" is available when a suggestion exists.

### Tests for User Story 1

- [X] T014 [P] [US1] Unit test `GeoDistance` (great-circle distance + bounding box) in `rg-logic/src/test/java/vg/rg/geo/GeoDistanceTest.java`
- [X] T015 [P] [US1] Unit test `LocationServiceImpl.findNearby` (Mockito `MockitoExtension`): radius filter, nearest-first ordering, empty result, invalid-coordinate rejection, `location:view` denial — in `rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java`
- [X] T016 [US1] Func test (`BaseFuncTest`, MySQL) proximity boundaries: ~100 m matches, ~2 km excluded, ~510 m excluded — in `rg-logic/src/test/java/vg/rg/service/LocationServiceFuncTest.java`

### Implementation for User Story 1

- [X] T017 [P] [US1] Create `GeoDistance` util (`metersBetween(...)`, `boundingBox(lat, lng, radiusMeters)`) in `rg-logic/src/main/java/vg/rg/geo/GeoDistance.java`
- [X] T018 [US1] Implement `LocationServiceImpl.findNearby` (bounding-box repo query via T007 → great-circle distance refine to ≤ radius → sort nearest-first; radius from `GeoProperties`; advisory only, `create` never consults it) in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java`
- [X] T019 [US1] Implement advisory suggestion UI in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java` — accept input coordinates, list nearby suggestions nearest-first, and always offer "add new anyway" (non-blocking, FR-003a); `@Route("locations", layout = MainView.class)`, `location:view` gating mirroring `ReportsView`

**Checkpoint**: Proximity suggestions work end-to-end against seeded data

---

## Phase 4: User Story 2 - Acquire coordinates via Google Maps (Priority: P1)

> **Superseded after implementation:** the Telegram *coordinate* fallback below was dropped — coordinates
> are now **optional**, so when Maps is unavailable the add form opens with none (no Telegram acquisition,
> no "no coordinate source" state). Telegram/browser geolocation now only **centres** the map picker
> (browser geolocation → Telegram `LocationManager` → Kyiv). The tasks are kept as the historical record
> of what was originally built; see the current-state spec and research.md R8 for the reconciled design.

**Goal**: In the add flow, open a Google Maps picker to capture a Place ID + coordinates or coordinates alone; if Maps is unavailable, fall back to Telegram coordinates only when the principal is Telegram-authenticated, else show a clear "no coordinate source" state.

**Independent Test**: With Maps available, pick a place → Place ID + coordinates captured; pick a point → coordinates only. Simulate Maps unavailable with a Telegram-authenticated principal → coordinates come from Telegram; with a non-Telegram principal → localized "no coordinate source" state with retry, no hang.

### Tests for User Story 2

- [X] T020 [P] [US2] Test `MapsResolutionBridge` validation (accept `{placeId?,lat,lng}` incl. Place-ID-absent payloads, reject out-of-range/oversized) in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/MapsResolutionBridgeTest.java`

### Implementation for User Story 2

- [X] T021 [US2] Create `google-maps-connector.ts` in `rg-frontend-vaadin/src/main/frontend/google-maps-connector.ts` — load Maps JS (referrer-restricted key from config, finite timeout) and present a **picker** returning either `{placeId,lat,lng}` or `{lat,lng}`; on Maps failure, **first feature-detect the Telegram WebApp location capability** (per research.md R8) and, when available and permitted, request coordinates and emit `{lat,lng}`; if the capability is unsupported/denied or neither source yields coordinates, signal the "no coordinate source" outcome (FR-004a) — do not assume the Telegram location API exists
- [X] T022 [US2] Create `MapsResolutionBridge` (`@ClientCallable`/component) in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/MapsResolutionBridge.java` — validate ranges and optional `placeId` length, invoke `LocationService.findNearby`; expose whether the principal is Telegram-authenticated (`AuthenticatedUserPrincipal.authenticationFlow == TELEGRAM`, via the app security context) so the client knows if the Telegram fallback is permitted
- [X] T023 [US2] Wire the connector ↔ bridge into the add flow in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java` — Maps picker first, Telegram fallback only when permitted, and the localized "no coordinate source available" retry state (FR-004a); server-side stays functional without Maps

**Checkpoint**: Coordinate acquisition (Maps primary, Telegram fallback) feeds the proximity suggestion

---

## Phase 5: User Story 3 - Add a location (Priority: P1)

**Goal**: Orchestrate the registration flow — Add button → acquire coordinates (US2) → proximity suggestion (US1) → pick an existing location or create a new one → confirm name (+ optional description/Place ID) → save.

**Independent Test**: From the Add button, acquire coordinates, see nearby suggestions, choose "add new", enter a name, save; confirm the location persists with author/version set; confirm picking a suggestion instead creates nothing; confirm adding within 500 m succeeds.

### Tests for User Story 3

- [X] T024 [P] [US3] Unit test `LocationServiceImpl.create` (name required, coordinate ranges, optional Place ID, `location:add` denial) — extend `rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java`
- [X] T025 [P] [US3] Func test create persists via `saveWithNewUniqueId` and populates `author`/`createdAt`/`version` — in `rg-logic/src/test/java/vg/rg/service/LocationServiceFuncTest.java`

### Implementation for User Story 3

- [X] T026 [US3] Implement `LocationServiceImpl.create` (validate → `repository.saveWithNewUniqueId(entity, uniqueIdService)` → map back) in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java`
- [X] T027 [US3] Create `LocationFormDialog` (add mode) in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationFormDialog.java` — pre-filled with the acquired coordinates (Place ID optional), name/description fields, localized validation feedback, anti-PII guidance on free-text (FR-018), `location:add` gating
- [X] T028 [US3] Implement the "Add location" entry + decision step in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java` — trigger acquire→suggest, then let the user pick a suggested existing location (no create) or choose "add new anyway" to open the form (adding within radius allowed, FR-004b/FR-003a)

**Checkpoint**: Full add flow works end-to-end; MVP (US1+US2+US3) demoable

---

## Phase 6: User Story 4 - Search locations by name (Priority: P2)

**Goal**: Filter the shared collection by name text.

**Independent Test**: With several locations, type a partial name → only matches shown; clear → full list; no-match → localized empty state.

### Tests for User Story 4

- [X] T029 [P] [US4] Unit test `LocationServiceImpl.searchByName` (contains-ignore-case, `limit` bound, blank → all) — extend `rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java`

### Implementation for User Story 4

- [X] T030 [US4] Implement `LocationServiceImpl.searchByName` (via `findByNameContainingIgnoreCase`, bounded by `GeoProperties.maxNameSearchResults`) in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java`
- [X] T031 [US4] Add name search field, live filtering, and no-results state to `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java`

**Checkpoint**: Name search works alongside proximity suggestion

---

## Phase 7: User Story 5 - Display saved locations (Priority: P2)

**Goal**: Browse the shared collection and view details, with a map preview / open-in-Maps link when a Place ID is present.

**Independent Test**: Open the list (paged), open a detail view; confirm preview/link derives from Place ID + coordinates; unresolved Place ID → graceful "preview unavailable"; empty state and permission-denied routing work.

### Tests for User Story 5

- [X] T032 [P] [US5] View test for `LocationsView` list/detail render, empty state, and permission-denied routing (→ `NoAccessView`/`AccessDeniedErrorView`) in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/LocationsViewTest.java`

### Implementation for User Story 5

- [X] T033 [US5] Implement `LocationServiceImpl.browse(Pageable)` (paged listing) in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java`
- [X] T034 [US5] Implement list + detail in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationsView.java` — mobile-first cards, detail with "open in Google Maps" link derived from Place ID + coordinates (no stored URL), unresolved-Place-ID graceful state
- [X] T035 [US5] Add a `Locations` navigation entry gated on `location:view` in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/MainView.java` (mirror the existing `Reports` nav gating)

**Checkpoint**: Collection is browsable and viewable on narrow screens

---

## Phase 8: User Story 6 - Edit or remove a location (Priority: P3)

**Goal**: Edit an existing location (optimistic concurrency) or remove it (protected), permission-gated.

**Independent Test**: Edit a field → persists with bumped version + lastEditor; two concurrent edits → second rejected with localized reload/retry; delete requires confirmation.

### Tests for User Story 6

- [X] T036 [P] [US6] Unit test `update` (stale `version` → `ObjectOptimisticLockingFailureException`; validation; `location:edit`) and `delete` (`location:delete`) — extend `rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java`
- [X] T037 [US6] Func test concurrency: two updates from the same loaded `version` → second fails with optimistic lock — in `rg-logic/src/test/java/vg/rg/service/LocationServiceFuncTest.java`

### Implementation for User Story 6

- [X] T038 [US6] Implement `LocationServiceImpl.update` (version-checked save, sets `lastEditor`) and `delete` in `rg-logic/src/main/java/vg/rg/service/LocationServiceImpl.java`
- [X] T039 [US6] Add edit + delete (with confirmation) to `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LocationFormDialog.java` / `LocationsView.java`, surfacing the localized `exception.ObjectOptimisticLockingFailureException` reload/retry message and `location:edit`/`location:delete` gating

**Checkpoint**: Full CRUD with safe concurrency and permissions

---

## Phase 9: Polish, Cross-Cutting & Spec Actualization

**Purpose**: Verification across stories, and the constitution-mandated actualization of the current spec set

- [X] T040 [P] View test for permission-denied and optimistic-lock feedback across the form/dialog in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/LocationFormDialogTest.java`
- [X] T041 Verify mobile-first layout (no horizontal scroll, reachable actions) and i18n in the default locale + one non-default locale (SC-005), across `LocationsView`/`LocationFormDialog`
- [X] T042 Verify no personal data appears in DB rows, logs, or telemetry; `author`/`lastEditor` are abstract `UniqueId` values only (SC-006)
- [X] T043 Run the narrowest verifications first, then broaden: `./gradlew :rg-logic:test`, then `./gradlew :rg-frontend-vaadin:test`, then `./gradlew test`
- [ ] T044 Execute [quickstart.md](quickstart.md) validation scenarios end-to-end
- [X] T045 **[Actualization]** Update `specs/current/` to reflect the implemented geolocation module — create/refresh the self-explaining domain file(s) (e.g., `specs/current/geolocation.md`), add cross-references, and remove any superseded content, per the constitution's final actualization requirement

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Setup — **BLOCKS all user stories**
- **User Stories (Phases 3–8)**: all depend on Foundational
  - The add flow is orchestrated in US3, which composes US2 (acquire coordinates) → US1 (proximity
    suggestion) → create. So US2 and US1 should land before US3's flow wiring (T028); all three are P1
  - US4, US5, US6 depend only on Foundational (independently testable with seeded data)
- **Polish/Actualization (Phase 9)**: depends on all targeted stories being complete

### Story Completion Order (recommended)

US1 → US2 → US3 (the three P1 stories form the MVP) → US5 → US4 → US6.

### Within Each User Story

- Tests written first and expected to fail → then implementation
- Models before services; services before UI; core before integration

### Parallel Opportunities

- Setup: T001–T004 all `[P]`
- Foundational: T007, T008, T009, T013 `[P]` after T005; T006 follows T005; T010–T012 follow the models/repo
- US1: T014, T015, T017 `[P]`; T018 after T017 (+T007); T019 after T018
- Shared-file note: tasks touching `LocationServiceImpl.java` (T018, T026, T030, T033, T038) and
  `LocationsView.java` (T019, T023, T028, T031, T034) are **sequential** within each file — not `[P]`
  across stories

---

## Parallel Example: User Story 1

```bash
# Tests (write first, expect failure):
Task: "Unit test GeoDistance in rg-logic/src/test/java/vg/rg/geo/GeoDistanceTest.java"
Task: "Unit test LocationServiceImpl.findNearby in rg-logic/src/test/java/vg/rg/service/LocationServiceImplTest.java"

# Then parallel implementation of independent files:
Task: "Create GeoDistance util in rg-logic/src/main/java/vg/rg/geo/GeoDistance.java"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational)
2. Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) — the three P1 stories: suggest, acquire, add-flow
3. **STOP and VALIDATE**: run the Add flow — acquire (Maps or Telegram fallback) → suggest → pick/create
4. Deploy/demo

### Incremental Delivery

Foundation → US1+US2+US3 (MVP) → US5 (display) → US4 (name search) → US6 (edit/remove) → Polish +
Actualization. Each story is an independently testable increment.

---

## Notes

- Tests are mandatory here (constitution); write them before implementation and verify they fail first.
- DB code follows `TemplateEntity`/`TemplateRepository`: `UniqueIdEntity` + `UniqueIdJpaRepository<...>`
  + `saveWithNewUniqueId`.
- Optimistic concurrency is provided by the entity `@Version`; the localized failure message key
  (`exception.ObjectOptimisticLockingFailureException`) already exists.
- Use `MockitoExtension` in unit tests; do not verify already-stubbed interactions (per AGENTS.md).
- Commit after each task or logical group; do not commit without an explicit instruction.
- **T045 is required** — a change is not complete until `specs/current/` is actualized.
