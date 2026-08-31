# Geolocation

Current-state specification of the geolocation module as implemented. Full requirements, clarifications,
and rationale live in [../002-geolocation-module/spec.md](../002-geolocation-module/spec.md); design in
[../002-geolocation-module/plan.md](../002-geolocation-module/plan.md).

## Purpose

Authenticated, permission-holding users maintain a **shared collection of saved locations** (points of
interest) and, when adding one, are shown already-registered places nearby so they can reuse an existing
one instead of creating a duplicate.

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
  coordinate fallback** — the add form opens directly so a permitted user can save a location **without
  coordinates** (name/description only). Coordinates are optional throughout.
- **Proximity suggestion**: given coordinates, suggest saved locations within a configurable radius
  (default **±500 m**), nearest-first. Advisory only — the user may always create a new location, even
  within the radius (no dedup/uniqueness gate).
- **Name search**: case-insensitive filter over the shared collection; blank query returns all
  (bounded); clear returns to the full list; empty result shows a no-results state.
- **Display**: browsable list of the collection and a detail view with name, description, and — **when
  present** — coordinates, the **Google Place ID**, and an "open in Google Maps" action (derived on
  demand from coordinates, refined by the Place ID). A location saved without coordinates simply omits
  the coordinates line and the maps link. The action opens via `Telegram.WebApp.openLink` inside a Mini
  App (a plain `target=_blank` anchor does not open in the Telegram webview), falling back to
  `window.open` in a normal browser.
- **Edit / remove**: update an existing location (optimistic concurrency) or delete it (with a
  confirmation step).

## Data

**Location** (table `rg_location`): coordinates (latitude/longitude, `DECIMAL(9,6)`, **optional/nullable**
— the proximity match key when present, **not** unique), name (required), description (optional),
optional **Google Place ID** (no Maps URL is stored — the link is derived), a version token for
optimistic concurrency, `author` and `lastEditor` (abstract user `UniqueId`, audit only), and
created/updated timestamps.

No personal data about natural persons is persisted; free-text fields show localized guidance
discouraging others' personal data but are stored as-is (the user's own content).

## Access control

Governed by the application's existing permissions model via `location:view`, `location:add`,
`location:edit`, `location:delete` (colon syntax, in `Permissions.Location`). Ownership is never used
for access; author/last-editor are audit-only. Concurrent edits use optimistic concurrency (JPA
`@Version`); a stale save is rejected with the localized "reload and retry" message.

## Where it lives

- **`rg-logic`** (business logic): `LocationService` (public interface) / `LocationServiceImpl`
  (package-private), `LocationEntity`, `LocationRepository` (bounding-box query + name search),
  `LocationMapper`, `GeoDistance` (great-circle distance + bounding box), `GeoProperties`
  (`rg.geo.match-radius-meters`, default 500), `CurrentUserAuditorAware`, and the `location:*`
  permissions. Schema: `rg-logic/src/main/resources/db/liquibase/001-location-init.yaml`.
- **`rg-frontend-vaadin`** (UI, mobile-first, i18n): `LocationsView` (`/locations`, gated on
  `location:view`, in the nav), `LocationFormDialog` (add/edit), `MapsResolutionBridge` (validates
  browser-acquired coordinates and runs the proximity suggestion), `MapsClientProperties` (browser
  config), and the browser connector `../../rg-frontend-vaadin/src/main/frontend/google-maps-connector.ts`. The connector loads
  the Google Maps JS API on demand and exposes `rgInitGoogleMapsConnector` (the map picker, including
  best-effort centering); results return via the `LocationsView` `@ClientCallable` methods
  (`onCoordinatesAcquired`, `onMapsUnavailable`). Server→client element wiring passes the view element
  explicitly as `$0` (so `$0.$server.*` resolves).
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
