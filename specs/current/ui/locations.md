# Locations

## Purpose and actors

Workspace owners manage saved places and select points through a browser map.

## Routes and entry conditions

`/workspaces/locations` runs within the active [workspace layout](workspaces.md).
Navigation and controls use scoped authorization.
A permitted create action exposes Browse and Add tabs; otherwise Browse is rendered
directly without tab captions.

## Interactions and outcomes

### Browse, edit, and remove

- A case-insensitive name filter requests up to 20 locations in the active workspace.
  Blank or cleared input shows the alphabetical first page; no matches has a localized
  empty result state. Domain ordering and limits belong to [locations logic](../logic/workspace/locations.md).
- Rows use the [shared disclosure list](shared-interactions.md#disclosure-lists).
  Details show name, description, and coordinates/Google Place ID when present.
- Coordinate-bearing locations offer Open in Google Maps. The URL is derived from
  coordinates, refined by Place ID, rather than stored. It uses Telegram
  `WebApp.openLink` when available and `window.open` in a normal browser.
- Locations without coordinates omit both the coordinate line and map action.
- Shared bordered-theme edit and remove actions open an edit dialog or deletion confirmation.
  Stale edits show localized reload/retry guidance. Referenced locations can be refused
  by the domain's restrictive event foreign key.

### Add journey

Selecting the Add tab caption opens a Google Maps modal with search, a fixed center
pin over an interactive map, and Use this location.

| Choice | Submitted data |
| --- | --- |
| Search address/place with Places autocomplete | Coordinates and Place ID |
| Select a labeled map point of interest | Coordinates and Place ID |
| Tap an empty point or drag the map under the pin | Coordinates only |

The selected place name or center coordinates are visible before confirmation.
Server-side coordinate validation and nearby-place lookup run through
`MapsResolutionBridge`. Nearby suggestions are scoped to the active workspace,
nearest-first, and advisory: the user can reuse a saved place or create another.

If Maps is unavailable, the form opens without coordinates so name/description can
still be saved. Browser/Telegram geolocation is not an alternative source of the
saved coordinate. Successful creation selects Browse, filters by the saved name,
and smoothly scrolls the new row into view.

## Functional requirements

- **FR-001**: Browse MUST filter names within the active workspace and request at most 20 results in domain-defined alphabetical order.
- **FR-002**: Coordinate-bearing details MUST derive an Open in Google Maps link from the coordinates and optional Place ID.
- **FR-003**: Choosing Add MUST open the map selection journey; if Maps is unavailable, the UI MUST allow saving a name/description without coordinates.
- **FR-004**: Acquired browser coordinates MUST pass server validation before a nearby-location query in the active workspace.
- **FR-005**: Nearby suggestions MUST allow the user to reuse a saved location or create a separate one.
- **FR-006**: Successful creation MUST select Browse, filter by the saved name, and request scrolling to the created location.

## States and failures

Empty workspaces and unmatched searches show localized messages. Stale edits show
reload/retry guidance. Deletion refusals use localized feedback. Map failure permits
coordinate-free entry, and geolocation failure falls through the centering sequence.

## Presentation and platform constraints

Map centering is best-effort and non-blocking: fresh high-accuracy browser geolocation,
then Telegram `LocationManager`, then the Kyiv default. Telegram initialization
waits for `locationManagerUpdated`; a denied location request can open settings and
a later grant can recenter the map. Centering is separate from confirming a location.

Maps runs only in the browser; CRUD/proximity logic does not depend on Google.
Secure-origin and integration settings/timeouts are documented in
[engineering configuration](../engineering/configuration.md#google-maps-browser-integration).
[Foundations](foundations.md) govern mobile layout and localization.

## Invariants

- **INV-001**: Proximity lookup uses the workspace identifier supplied by the server view to the bridge (checked by `MapsResolutionBridgeTest`).
- **INV-002**: Repeated removal confirmation causes at most one deletion (checked by `WorkspaceLocationsViewTest`).
- **INV-003**: Rejected browser coordinates do not reach proximity lookup (checked by `MapsResolutionBridgeTest`).

## Success criteria

- **SC-001**: Successful creation displays the saved location on Browse with its saved-name filter (`WorkspaceLocationsViewTest`).
- **SC-002**: Saving a blank name marks the field invalid and performs no persistence call (`LocationFormDialogTest`).
- **SC-003**: Browser coordinates outside the valid ranges are refused at the server bridge (`MapsResolutionBridgeTest`).

## Related contracts

- [Location logic](../logic/workspace/locations.md), [workspaces](workspaces.md), and [shared interactions](shared-interactions.md).
- [Google Maps configuration](../engineering/configuration.md#google-maps-browser-integration).

## Evidence

- [WorkspaceLocationsView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLocationsView.java)
- [LocationFormDialog.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/LocationFormDialog.java)
- [MapsResolutionBridge.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/MapsResolutionBridge.java)
- [google-maps-connector.ts](../../../rg-frontend-vaadin/src/main/frontend/ts/maps/google-maps-connector.ts)
- [WorkspaceLocationsViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLocationsViewTest.java)
- [LocationFormDialogTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/LocationFormDialogTest.java)
- [MapsResolutionBridgeTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/MapsResolutionBridgeTest.java)
