# Phase 0 Research: Geolocation Module

All Technical Context unknowns are resolved below. Decisions favor the existing codebase patterns
(Template*/ProtectedAction*), constitution constraints, and simplicity.

## R1. Permission naming and authorization mechanism

**Decision**: Add four permissions to `vg.rg.security.model.Permissions` as a nested
`Location` class: `location:view`, `location:add`, `location:edit`, `location:delete`. Authorize each
service method with `@PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Location.X + "')")`,
exactly as `ProtectedActionServiceImpl` does for `request:submit`.

**Rationale**: The `Permissions.FORMAT` regex is `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` — a **colon**
separator, not a dot. The clarification used `location.add` informally; the enforced convention is
`location:add`. `Permissions.ALL` must include the new values (validated/frozen at class load).
Message keys follow the escaped form already used: `permission.location\:view`, etc.

**Alternatives considered**: Dotted names (`location.add`) — rejected: fail `hasValidFormat`. A single
coarse `location:manage` — rejected: spec needs distinct view/add/edit/delete gating (FR-010, edge case).

## R2. Entity, IDs, auditing, and optimistic concurrency

**Decision**: `LocationEntity implements UniqueIdEntity` with `@Id Long uniqueId`, `@Version int
version`, `@CreatedDate`/`@LastModifiedDate` (Instant), and **`@CreatedBy`/`@LastModifiedBy UniqueId`**
for author/last-editor (persisted via a `UniqueId`↔BIGINT `AttributeConverter`). Persist via
`repository.saveWithNewUniqueId(entity, uniqueIdService)` for
create and `repository.save(entity)` for update, mirroring `TemplateServiceImpl`.

**Rationale**: `@Version` gives optimistic concurrency for free — a stale update raises
`ObjectOptimisticLockingFailureException`, which is **already localized**
(`exception.ObjectOptimisticLockingFailureException`) — satisfying FR-019 with existing infrastructure.
`@EnableJpaAuditing` is already active (`RgLogicConfig`). `@CreatedBy`/`@LastModifiedBy` require
an `AuditorAware` bean (see R3).

**Alternatives considered**: Manual version checks in the service — rejected: reinvents JPA locking.
Storing author/editor by hand in the service — rejected: Spring Data auditing is the idiomatic path.

## R3. Author / last-editor source (audit only)

**Decision**: Add `CurrentUserAuditorAware implements AuditorAware<UniqueId>` returning the current
user (`AuthorityChecker.currentUserUniqueId()`, i.e., the abstract `userUniqueId`); register it so
`@EnableJpaAuditing(auditorAwareRef = ...)` populates `@CreatedBy`/`@LastModifiedBy`. A `UniqueId`↔BIGINT
`AttributeConverter` persists those columns.

**Rationale**: `userUniqueId` is an abstract, opaque identifier (constitution-compliant; not personal
data). Recording it as author/editor is audit-only and never used for access decisions (FR-010).

**Alternatives considered**: Storing display `name` — rejected: constitution forbids persisting the
display name and it is not an identity input.

## R4. Proximity search (±500 m, configurable) over MySQL and H2

**Decision**: Store `latitude`/`longitude` as `DECIMAL(9,6)` (≈0.11 m resolution). Proximity query is a
two-step, DB-portable approach:
1. **Bounding-box prefilter** in a `@Query` using indexed lat/lng columns: `lat BETWEEN :minLat AND
   :maxLat AND lng BETWEEN :minLng AND :maxLng`, where the box is derived from the radius
   (Δlat = radius / 111_320 m; Δlng = radius / (111_320 · cos(lat))).
2. **great-circle distance refinement + ordering** in the service (Java): compute great-circle distance for the
   prefiltered rows, keep those ≤ radius, sort nearest-first.

Default radius **500 m**, injected via `GeoProperties` (`rg.geo.match-radius-meters`, configurable).

**Rationale**: Works identically on MySQL (func tests) and H2 (frontend dev) with no spatial-type or
extension dependency (constitution "simplicity mandatory"). The bounding box uses ordinary B-tree
indexes and keeps the candidate set tiny even at ≥10k rows, so the app-owned response stays instant
(SC-001, SC-003). Java-side great-circle distance keeps the exact boundary behavior deterministic and unit-testable
(SC-001: ~100 m match, ~2 km no-match; and the "just outside 500 m" edge case).

**Alternatives considered**: MySQL `ST_Distance_Sphere`/`POINT`+SPATIAL index — rejected: not portable
to H2 dev runtime, heavier for the expected scale. Pure in-memory scan — rejected: unbounded read as
the collection grows. A dedicated geo store (PostGIS/Elasticsearch) — rejected: new infra needs
Governance approval and is unjustified at this scale.

## R5. Google Maps integration boundary and add-flow coordinate acquisition

**Decision**: Confine Google Maps to the **browser** in `rg-frontend-vaadin`. The "Add location" flow
acquires coordinates in this order:
- **Primary — Google Maps picker**: a TypeScript connector (`google-maps-connector.ts`) opens a modal
  with a search box, an interactive map with a fixed centre pin, and a confirm button. It uses the
  **modern (non-legacy) Places surface** — `PlaceAutocompleteElement` for search and `Place.fetchFields`
  (`displayName`, `location`) for details — requiring **Maps JavaScript API** + **Places API (New)** in
  the Google Cloud project (no Geocoding / server-side Places). The user selects the point under the pin
  three ways: search a place → `{ placeId, lat, lng }`; tap a labelled place (POI) → `{ placeId, lat,
  lng }`; tap an empty spot / drag the map → `{ lat, lng }` (coordinates only). The result posts to a
  Vaadin `@ClientCallable`/bridge (`MapsResolutionBridge`) that validates ranges (and optional `placeId`
  length) then runs the server-side proximity suggestion. An optional Vector Map ID
  (`google.maps.map-id`) enables the modern vector (WebGL) map.
- **Map centering (not acquisition)**: the picker best-effort centres on the user's location — browser
  geolocation first (a forced-fresh, high-accuracy GPS fix), then the Telegram `LocationManager` as a
  fallback (covers iOS), otherwise a **Kyiv** default. This only positions the map; it is never the saved
  coordinate. (Telegram's `getLocation` can return a coarse/last-known fix and has no "refresh", so it is
  the fallback, not the primary, for centering — see R8.)
- **No-Maps fallback**: if Maps fails to load / times out / is unavailable there is **no coordinate
  fallback** — the add form opens directly so a permitted user can save a location **without
  coordinates** (they are optional). No hang, no fabricated coordinates (FR-004a/FR-013). *(This
  supersedes the earlier "acquire coordinates from Telegram when the principal is Telegram-authenticated"
  fallback: coordinates are now optional, so an absent source is simply a location without coordinates.)*
- **Preview / open-in-Maps**: derive the URL on demand from Place ID + coordinates
  (`https://www.google.com/maps/search/?api=1&query=<lat>,<lng>&query_place_id=<placeId>`); no key, no
  stored URL (FR-009).
- The browser API key is **referrer-restricted**, supplied via runtime config
  (`google.maps.browser-api-key` placeholder in `application.properties`, value from env), never
  committed, logged, or shown in errors.

**Rationale**: Matches the user-specified add flow (Add → Maps picker → proximity suggestion →
pick/create). Server-side proximity/CRUD stays fully functional without Google Maps (Principle IV,
FR-013); coordinates are **optional**, so when Maps is unavailable the user simply adds a location
without them rather than depending on a second acquisition source. A browser Maps JS key is designed to
be public but is still treated as configurable and referrer-scoped (Principle II). Telegram is used only
to *centre* the map (no new identity handling — Principle III), never to acquire the saved coordinate.
This resolves the `/speckit-clarify` deferred item (Maps input modality) → **map picker (search +
PlaceAutocompleteElement, POI tap, or map point); optional coordinates when Maps is unavailable**.

**Secure-origin note**: the Mini App must be served over **https** — Telegram `LocationManager` and
browser geolocation only work in a secure context. As the app runs plain HTTP behind a TLS-terminating
proxy/tunnel, `server.forward-headers-strategy=framework` is set so `X-Forwarded-Proto` is honoured
(otherwise a first-load redirect drops the webview to an http origin and location silently fails).

**Alternatives considered**: Server-side Places API calls — rejected for MVP: adds a server secret,
outbound dependency, and quota handling for a capability the browser already provides. Telegram as a
coordinate source — dropped: coordinates are optional, so an unavailable Maps just yields a location
without coordinates; Telegram now only *centres* the map (R8). Manual coordinate typing — not required
for v1 (a manual lat/lng entry affordance nonetheless exists as a secondary preview aid).

## R6. Failure handling and timeouts for Maps

**Decision**: The connector applies a finite timeout to Maps script load and lookups; on
timeout/unavailability it opens the add form with **no coordinates** (coordinates are optional) — no
hang, no fabricated result (FR-004a/FR-013). Unresolved Place IDs at display time show stored details
with a graceful "preview unavailable" state.

**Rationale**: Satisfies FR-013/FR-014 and constitution Principle IV (finite timeouts, no hang, no
fabricated success, recoverable partial state). No end-to-end latency SLO is set for Maps-dependent
flows.

**Alternatives considered**: Unbounded waits / silent failure — rejected (constitution violation).

## R7. Testing strategy

**Decision**:
- `rg-logic`: unit tests (Mockito `MockitoExtension`) for `LocationServiceImpl` (validation, permission
  denial, audit population, optimistic-lock rejection) and great-circle distance/bounding-box math; DB-backed
  func tests (`BaseFuncTest`, MySQL) for the proximity query at boundary distances and for concurrency
  (`@Version`) behavior; a `PermissionsTest` extension asserting the new `location:*` values are
  recognized and well-formed.
- `rg-frontend-vaadin`: view tests for `LocationsView`/`LocationFormDialog` covering render, name
  search filtering, empty/no-results/permission-denied routing (mirroring `ReportsView`), and
  localized optimistic-lock feedback.

**Rationale**: Matches existing coverage patterns and the constitution's mandatory unit + integration
testing, including boundary and failure paths.

**Alternatives considered**: UI end-to-end (Playwright) for Google Maps — deferred: external,
browser-key dependent, and outside the app-owned test boundary.

## R8. Telegram WebApp location capability (map centering only)

**Decision**: Telegram `Telegram.WebApp.LocationManager` (Bot API 8.0+) is used **only to centre the map
picker**, never to acquire the saved coordinate, and only as a **fallback after browser geolocation**.
The centre chain is: **browser geolocation first** (`enableHighAccuracy`, `maximumAge: 0` — a fresh,
accurate GPS fix), then Telegram `LocationManager` (covers iOS, where the W3C geolocation API is
unreliable in the webview), otherwise a **Kyiv** default. Capability-detected; any failure just leaves
the map on the default. No principal/auth gating is needed since centering carries no coordinate into
the record.

**As implemented**, two on-device findings shaped this: (a) it requires a secure **https** origin — over
http `init()` hangs with no client response; (b) init()'s own callback is unreliable, so the code
proceeds on the **`locationManagerUpdated` event**. The Telegram-fallback sequence is `WebApp.ready()` →
`init()` (empty callback; proceed on the event) → `getLocation()`; when access is not granted it calls
`openSettings()` and re-centres via a `locationManagerUpdated` listener once the user grants — all
**non-blocking**, so the browser result is never starved. Telegram is *not* used as the accurate source
because `getLocation()` can return a coarse/last-known fix and has **no "refresh"**, whereas browser
geolocation can force a fresh high-accuracy fix.

**Rationale**: The accurate, controllable source is browser geolocation; Telegram is the cross-platform
safety net (notably iOS). Centering is cosmetic — correctness never depends on it (the saved coordinate
comes only from the Maps picker), so an absent/denied/coarse Telegram fix degrades gracefully to the
browser fix or the Kyiv default, with no new identity handling (Principle III).

**Alternatives considered**: Telegram as the primary/only centering source — rejected: it returned a
wrong (coarse) fix on-device and offers no way to force a fresh one. Using Telegram to acquire the saved
coordinate — dropped: coordinates are optional, so no second acquisition source is needed (R5/R6).

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| Permission names/format | `location:view/add/edit/delete` (colon), in `Permissions.Location` |
| Concurrency mechanism | JPA `@Version` + existing localized optimistic-lock message |
| Author/editor source | `AuditorAware<UniqueId>` → abstract `userUniqueId` (audit only; `UniqueId`↔BIGINT `@Convert`) |
| Proximity over MySQL+H2 | Bounding-box `@Query` + Java great-circle distance, radius configurable (500 m default) |
| Coordinate acquisition (add flow) | Maps picker only (search / POI tap → `{placeId,lat,lng}`; map point → `{lat,lng}`). Coordinates are **optional**: if Maps is unavailable, the add form opens with none |
| Maps APIs / key handling | Maps JavaScript API + Places API (New) (modern `PlaceAutocompleteElement`/`Place`); referrer-restricted browser key + optional Vector `google.maps.map-id`, via runtime config, never stored/logged |
| Preview/open link | Derived from Place ID + coordinates (only when present); not persisted; opened via `Telegram.WebApp.openLink` (fallback `window.open`) |
| Telegram location | **Map centering only** (never the saved coordinate), as a fallback after browser geolocation: `ready`→`init`/`locationManagerUpdated`→`getLocation`→`openSettings` (non-blocking); requires https; failure → browser fix or Kyiv default (R8) |
| Secure origin | https required; `server.forward-headers-strategy=framework` so `X-Forwarded-Proto` is honoured behind the TLS-terminating proxy |
