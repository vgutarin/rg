# Quickstart & Validation: Geolocation Module

A run/validation guide proving the feature works end-to-end. Implementation details live in
`data-model.md`, `contracts/`, and (later) `tasks.md`.

## Prerequisites
- JDK 21; Docker running (Testcontainers/MySQL for `rg-logic` func tests) — see the `colima-setup`
  skill if Docker is not configured on macOS.
- For UI + Google Maps: a referrer-restricted Google Maps JavaScript API key exported as
  `GOOGLE_MAPS_BROWSER_API_KEY` (the app reads `google.maps.browser-api-key`).
- A test user whose secure-service permissions include `location:view`, `location:add`,
  `location:edit`, `location:delete`.

## Build & test (server-side logic)

Narrowest first, then broaden (per AGENTS.md):

```bash
./gradlew :rg-logic:test
```

Expected: green, including
- `LocationServiceImpl` unit tests: validation, permission denial, audit population, optimistic-lock
  rejection.
- Proximity func tests (MySQL): a location at known coordinates **matches** a query ~100 m away and
  **does not match** one ~2 km away; a location ~510 m away is **excluded** (boundary, SC-001).
- Concurrency func test: two updates from the same loaded `version` → second raises
  `ObjectOptimisticLockingFailureException` (FR-019).
- `PermissionsTest`: the four `location:*` values are recognized, well-formed, and in `ALL`.

```bash
./gradlew :rg-frontend-vaadin:test
```

Expected: `LocationsView`/`LocationFormDialog` tests pass — render, name-search filtering, empty and
no-results states, permission-denied routing (→ `NoAccessView`/`AccessDeniedErrorView`), and localized
optimistic-lock feedback.

Full sweep when both modules change:

```bash
./gradlew test
```

## Run the app (manual UI validation)

```bash
GOOGLE_MAPS_BROWSER_API_KEY=... ./gradlew :rg-frontend-vaadin:bootRun
```

Open `http://localhost:9000` in a narrow (mobile) viewport, authenticate via the Telegram flow, then:

| Scenario | Steps | Expected (spec ref) |
|----------|-------|---------------------|
| Add flow — Maps picker (P1) | Click "Add location" → in the Maps picker select a place, then a plain point | Place ID + coordinates captured for the place; coordinates only for the point (US2 sc.1) |
| Proximity suggestion (P1) | After acquiring coordinates ~100 m from a saved location | The saved location is suggested nearest-first (advisory), not auto-selected (US1) |
| No suggestion → add | Acquire coordinates >500 m from any saved location | No suggestion + offer to create with coordinates pre-filled (US1 sc.2, US3 sc.1) |
| Pick existing | Acquire coordinates within 500 m, then pick a suggestion | No new location created; existing one selected/opened (US3 sc.2) |
| Add despite suggestion | Acquire coordinates within 500 m, then choose "add new anyway" → name → save | New location created within the radius; no block/merge (US3 sc.3, FR-003a) |
| Add without coordinates (P1) | Block/deny the Maps script, then "Add location" | Add form opens directly; save with name only → location persists without coordinates; no hang (US2 sc.2, FR-004a) |
| Detail without coordinates | Open a location saved without coordinates | Coordinates line and "open in Google Maps" are omitted; other details render (FR-006) |
| Map centering (best-effort) | Open the Maps picker | Map centres on your location (browser geolocation; Telegram `LocationManager` fallback; Kyiv default) — centring only, never the saved point (R8) |
| Maps unavailable — rest works | With Maps blocked, browse/search/edit/delete and proximity on acquired coords | All remain functional; new locations are added without coordinates; preview degrades (SC-004, FR-013) |
| Name search (P2) | Type part of a name | List filters to matches; clearing restores full list; "no results" state works (US4) |
| Display (P2) | Open a location with a Place ID | Detail shows map preview / "open in Google Maps" link derived from Place ID + coordinates (US5) |
| Edit conflict (P3) | Edit the same location from two sessions; save both | Second save rejected with localized "reload and retry"; no silent loss (FR-019) |
| Permission denied | Sign in as a user lacking `location:edit` | Edit action denied safely; routed per `ReportsView` pattern (FR-010) |
| Privacy notice | Focus name/description field | Localized guidance discouraging others' personal data is shown; text stored as-is (FR-018) |

## Definition of done (validation gates)
- All three Gradle test invocations above are green.
- Manual scenarios match the Expected column.
- No personal data in DB rows, logs, or telemetry; author/last-editor stored as abstract `userUniqueId` only
  (SC-006).
- Mobile viewport: no horizontal scroll; all actions reachable; verified in the default locale and one
  non-default locale (SC-005).
