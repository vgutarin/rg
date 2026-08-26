# Contract: Google Maps Browser Integration (rg-frontend-vaadin)

Google Maps is confined to the browser. The server never calls Google Maps; it only receives already
validated coordinates and an optional Place ID. This preserves resilience (Constitution IV) and keeps
identity/secrets bounded (Constitution II).

## "Add location" flow (coordinate acquisition → suggestion → pick/create)

1. User clicks **"Add location"**.
2. `google-maps-connector.ts` loads the Google Maps JavaScript API (referrer-restricted browser key from
   runtime config) and presents a **modal picker**: a search box, an interactive map with a fixed centre
   pin, and a "Use this location" button. It uses the **modern (non-legacy) Places surface** —
   `PlaceAutocompleteElement` for search and `Place.fetchFields({fields:['displayName','location']})` for
   details — so the Google Cloud project needs **Maps JavaScript API** + **Places API (New)** (no
   Geocoding / server-side Places). An optional Vector **Map ID** (`google.maps.map-id`) enables the
   vector map. The user chooses the point under the pin three ways:
   - **search** a place → **Place ID + coordinates** (+ display name shown);
   - **tap a labelled place (POI)** on the map → **Place ID + coordinates** (+ display name shown);
   - **tap an empty spot / drag** the map under the pin → **coordinates alone** (no Place ID).

   The picker best-effort **centres on the user's location** — *centering only, never the saved
   coordinate*: browser geolocation first (a forced-fresh, high-accuracy GPS fix), then the Telegram
   `LocationManager` as a fallback (covers iOS), otherwise a **Kyiv** default (see research.md R8).
3. **No-Maps fallback**: if Maps fails to load / times out / is unavailable, there is **no coordinate
   fallback**. The add form opens directly so a permitted user can save a location **without
   coordinates** (name/description only) — coordinates are optional. No hang, no fabricated coordinates.
   (Telegram is not used to acquire the saved coordinate; it only helps *centre* the map above.)
4. The connector calls the server bridge (`MapsResolutionBridge`, a Vaadin `@ClientCallable` or
   listener) with the acquired coordinates:

```json
{ "placeId": "ChIJ...", "lat": 37.42199, "lng": -122.08408 }
```

`placeId` is optional (omitted when the user picked a point only). When Maps is unavailable the add form
opens with no coordinates at all, and the location is saved without latitude/longitude.

**Server obligations**:
- Validate `lat ∈ [−90,90]`, `lng ∈ [−180,180]`, and `placeId` length ≤ 512 when present; reject
  otherwise (no partial state).
- Run `LocationService.findNearby(new ProximityQuery(lat, lng, null))` and present suggestions; the user
  picks an existing location or proceeds to create a new one (FR-004b, FR-003a).
- Never trust the payload as identity; coordinates and Place ID are opaque enrichment data only.

## Preview / open-in-Maps (no key, not stored)

Derived on demand from Place ID + coordinates (FR-009):
```
https://www.google.com/maps/search/?api=1&query=<lat>,<lng>&query_place_id=<placeId>
```
When `placeId` is absent, the same link is built from coordinates alone (the `query` fallback). A stored
`placeId` that fails to resolve upstream → show stored details + "preview unavailable" (graceful, FR-013).
The link is opened via `Telegram.WebApp.openLink(url)` inside a Mini App (a plain `target=_blank` anchor
does not open in the Telegram webview), falling back to `window.open` in a normal browser.

## Failure & timeout behavior
- Finite timeout on script load and lookup; on timeout/unavailable/unresolved → localized retry/fallback
  state; the user may still save without a Place ID or retry (FR-013, FR-014). No end-to-end latency SLO.

## Configuration & secrets
- `google.maps.browser-api-key` — placeholder in `application.properties`, value injected from env at
  runtime; referrer-restricted; never committed, logged, or shown in errors (Constitution II). When
  blank, the picker fails fast and the user adds a location without coordinates.
- `google.maps.map-id` — optional **Vector** Map ID (browser-side, non-secret, referrer-scoped); when set
  the picker renders the modern vector (WebGL) map, otherwise a classic raster map.

## Secure origin (https) requirement
- The Mini App must be served over **https**. Telegram `LocationManager` and browser geolocation only
  work in a secure context; over http the Telegram location handshake hangs and geolocation is blocked.
  Because the app runs plain HTTP behind a TLS-terminating proxy/tunnel,
  `server.forward-headers-strategy=framework` is set so `X-Forwarded-Proto` is honoured — otherwise a
  first-load redirect drops the webview to an http origin.

## Availability guarantee
- With Google Maps unavailable, proximity match (on already-acquired coordinates), browse, name search,
  edit, and delete all remain fully functional (SC-004, FR-013). New-location adding also stays
  available: the add form opens directly and the location is saved **without coordinates** (they are
  optional). Map *preview* / open-in-Maps simply degrades to being omitted when a location has no
  coordinates.
