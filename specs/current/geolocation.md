# Geolocation

Current-state specification of the geolocation module as implemented. Full requirements, clarifications,
and rationale live in [../002-geolocation-module/spec.md](../002-geolocation-module/spec.md); design in
[../002-geolocation-module/plan.md](../002-geolocation-module/plan.md). The workspace scoping that now
governs access is specified in [workspace.md](./workspace.md), with rationale in
[../003-workspace-layer/spec.md](../003-workspace-layer/spec.md).

## Purpose

Users maintain **saved locations** (points of interest) **inside a workspace**, and, when adding one, are
shown already-registered places nearby **in that same workspace** so they can reuse an existing one
instead of creating a duplicate.

A location exists only inside a workspace. The former single collection shared by every permission holder
is retired — see [Scope](#scope) below.

## Scope

Every location belongs to exactly one workspace, and that is a property of the schema rather than a
convention: `rg_workspace_location.workspace_unique_id` is **NOT NULL and never updated**. A location
without a workspace is unrepresentable, and a location cannot move between workspaces.

Consequently:

- Every read takes a workspace, so an unscoped query cannot be written — the repository has no overload
  that omits it.
- The navigation entry is gated on the *active workspace*, not on a permission the principal holds, so
  what the drawer offers and what the service will allow are the same question asked once each.
- The proximity suggestion never looks outside the active workspace, and neither does name search.
- Removing a workspace removes its locations and changes no other workspace's contents.
- A user without `workspace:owner` reaches **no** locations at all, because the only route to one is
  through its workspace.

## Capabilities

- **Add flow**: "Add location" → the **Google Maps picker** (a modal with a search box, an interactive
  map with a fixed centre pin, and a "Use this location" button). The user chooses a point three ways:
  (1) **search** an address/place (Places API New autocomplete) → coordinates **+ Place ID**;
  (2) **tap a labelled place (POI)** the map shows — metro, stadium, restaurant, etc. → coordinates
  **+ Place ID**; (3) **tap an empty spot or drag** the map under the pin → **coordinates only** (no
  Place ID). The selected place's name (1, 2) or the centre coordinates (3) are shown before confirming.
  The map best-effort **centres on the user's current position** to start — browser geolocation first (a
  forced-fresh, high-accuracy GPS fix), then the Telegram `LocationManager` as a fallback (covers iOS,
  where the browser API is unreliable), otherwise a **Kyiv** default. This is **centering only** — never
  the saved coordinate. On confirm, coordinates and the optional Place ID are handed to the server.
- **No-Maps fallback**: if Google Maps is unavailable (missing/blocked key, load timeout) there is **no
  coordinate fallback** — the add form opens directly so a user can save a location **without
  coordinates** (name/description only). Coordinates are optional throughout.
- **Proximity suggestion**: given coordinates, suggest locations **in the active workspace** within a
  configurable radius (default **±500 m**), nearest-first. Advisory only — the user may always create a
  new location, even within the radius (no dedup/uniqueness gate).
- **Name search**: case-insensitive filter **within one workspace**; blank query returns all (bounded);
  clear returns to the full list; empty result shows a no-results state.
- **Display**: browsable list of the workspace's locations and a detail view with name, description, and —
  **when present** — coordinates, the **Google Place ID**, and an "open in Google Maps" action (derived on
  demand from coordinates, refined by the Place ID). A location saved without coordinates simply omits
  the coordinates line and the maps link. The action opens via `Telegram.WebApp.openLink` inside a Mini
  App (a plain `target=_blank` anchor does not open in the Telegram webview), falling back to
  `window.open` in a normal browser.
- **Edit / remove**: update an existing location (optimistic concurrency) or delete it (with a
  confirmation step).

## Data

**Workspace location** (table `rg_workspace_location`): `workspace_unique_id` (**required, immutable** —
the scope, and the link the authority check joins through), coordinates (latitude/longitude,
`DECIMAL(9,6)`, **optional/nullable** — the proximity match key when present, **not** unique), name
(required), description (optional), optional **Google Place ID** (no Maps URL is stored — the link is
derived), a version token for optimistic concurrency, `author` and `lastEditor` (abstract user `UniqueId`,
audit only), and created/updated timestamps.

The superseded table `rg_location` is **retained on disk and entirely unmapped** — no entity, no
repository, nothing that reaches it — marked by a table comment as replaced by `rg_workspace_location`. It
is the rollback for the one-time copy into workspaces, which has itself been deleted; dropping the table
is a separate later change. See [workspace.md](./workspace.md#the-retired-global-scope).

No personal data about natural persons is persisted; free-text fields show localized guidance
discouraging others' personal data but are stored as-is (the user's own content).

## Access control

Governed by the workspace layer, not by app-wide location capabilities. The app-wide gate is
**`workspace:owner`**; the location capabilities (`location:read|list|create|update|delete`) live in
`LocalPermissions` and are **declared, not held** — **owning the workspace grants complete authority over
its locations**, so an owner holding none of them is still allowed.

Each service method guards with one resource-addressed check, passing the identifier of the thing being
acted on — the location's own id for `update`/`delete`, the workspace for `create` and for reads:

```java
@PreAuthorize("@authorityChecker.hasAuthority(#locationId, 'location:update')")
```

The location resolves to its workspace in a single joined query, so the check costs one round trip
regardless of how deep a type sits. Author and last-editor remain audit-only and are never used for
access. Concurrent edits use optimistic concurrency (JPA `@Version`); a stale save is rejected with the
localized "reload and retry" message.

## Where it lives

- **`rg-logic`** (business logic): `WorkspaceLocationService` (public interface) /
  `WorkspaceLocationServiceImpl` (package-private), `WorkspaceLocationEntity`,
  `WorkspaceLocationRepository` (workspace-scoped bounding-box query, name search, and the scope join),
  `WorkspaceLocationMapper`, `LocationScopeProvider` (resolves a location to its workspace),
  `LocationWorkspaceContentContributor` (removal with its workspace), `GeoDistance` (great-circle
  distance + bounding box), `GeoProperties` (`rg.geo.match-radius-meters`, default 500),
  `CurrentUserAuditorAware`, and the `location:*` capabilities in `LocalPermissions`. Schema:
  `rg-logic/src/main/resources/db/liquibase/002-workspace-init.yaml`, plus
  `003-retire-global-location-migration.yaml`; the superseded `001-location-init.yaml` remains, since its
  table is retained.
- **`rg-frontend-vaadin`** (UI, mobile-first, i18n): `WorkspaceLocationsView`
  (**`/workspaces/locations`**, reached from a **top-level** navigation entry shown only when
  `hasAuthority(activeWorkspaceId, "location:list")` holds — the workspace is the scope, not a place to
  navigate through), `LocationFormDialog` (edit), `MapsResolutionBridge` (validates browser-acquired
  coordinates and runs the **workspace-scoped** proximity suggestion — it requires a workspace, there is
  no unscoped overload), `MapsClientProperties` (browser config), and the browser connector
  `../../rg-frontend-vaadin/src/main/frontend/google-maps-connector.ts`. The connector loads the Google
  Maps JS API on demand and exposes `rgInitGoogleMapsConnector` (the map picker, including best-effort
  centering); results return via the view's `@ClientCallable` methods (`onCoordinatesAcquired`,
  `onMapsUnavailable`). Server→client element wiring passes the view element explicitly as `$0` (so
  `$0.$server.*` resolves).
- **Google Maps configuration** (all browser-side, non-secret, referrer-scoped):
  - `google.maps.browser-api-key` — the browser API key (required for the Maps picker; when blank the
    picker fails fast and the user adds a location without coordinates).
  - `google.maps.map-id` — optional **Vector** Map ID; when set the picker renders the modern vector
    (WebGL) map, otherwise a classic raster map.
  - **Google Cloud APIs to enable**: **Maps JavaScript API** and **Places API (New)**. No Geocoding /
    server-side Places APIs are used. The picker uses `PlaceAutocompleteElement`, `Place.fetchFields`
    (`displayName`, `location`), and POI `placeId` clicks — the modern (non-legacy) Places surface.

## Constraints

- Google Maps is confined to the browser; server-side proximity/CRUD never depend on it. Google Maps
  lookups use a finite, configurable timeout with clear retry/fallback and never fabricate a result.
- **Secure origin required**: the Mini App must be served over **https**. Browser geolocation and the
  Telegram `LocationManager` only work in a secure context; over http geolocation is blocked and the
  Telegram location handshake hangs. Because the app runs plain HTTP behind a TLS-terminating
  proxy/tunnel, `server.forward-headers-strategy=framework` is set so `X-Forwarded-Proto` is honoured —
  otherwise a first-load redirect drops the webview to an http origin.
- **Map centering (only)** resolves in order and non-blocking: browser geolocation first
  (`enableHighAccuracy`, `maximumAge: 0` for a fresh GPS fix — the accurate source), then Telegram
  `LocationManager` as a fallback (covers iOS). The Telegram path is `WebApp.ready()` → `init()` (proceed
  on the `locationManagerUpdated` event, not `init()`'s callback) → `getLocation()`; when access is not
  granted it calls `openSettings()` and re-centres via a `locationManagerUpdated` listener once the user
  grants — without blocking. Telegram is **never** used to acquire the saved coordinate (it can return a
  coarse/last-known fix and has no "refresh"); coordinate acquisition is Google-Maps-only.
- All user-facing text is internationalized (`messages*.properties`); flows are mobile-first.
- Performance is validated functionally (no fixed latency SLO on Google-Maps-dependent flows).
