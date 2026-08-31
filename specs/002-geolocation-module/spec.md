# Feature Specification: Geolocation Module

**Feature Branch**: `002-geolocation-module`

**Created**: 2026-08-26

**Status**: Draft

**Input**: User description: "Geolocation module. Add/Edit/Display/Search locations. Integration with google maps. Local storage + optional reference to google map (place id or url?)"

## Overview

Authenticated users can build and manage a shared collection of saved locations (points of
interest). Each location stores **optional** geographic **coordinates** (the primary match key when
present, not a uniqueness constraint), a **name**, an optional **description**, and an optional **Google
Place ID** referencing its Google Maps entry for richer viewing (an "open in Google Maps" link is derived
from the Place ID and coordinates on demand, when coordinates are present).

The "Add location" flow starts by acquiring coordinates from a **Google Maps picker** (a Place ID with
coordinates, or coordinates alone); if Maps is unavailable the location is added **without coordinates**
(coordinates are optional). With coordinates in hand, the
module's primary job is to **suggest** already-registered places within a small radius (±500 m) so the
user can pick an existing one instead of creating a duplicate. The suggestion is advisory — the user may
always add a new location instead, even when nearby matches exist (no dedup/uniqueness gate). Users can
also find locations by name, view them, and edit or remove existing ones. Locations are stored by this
application as a shared collection with no per-user cap; each record keeps the acting user's abstract
identity as author and last editor for auditing only, and this identity is never used to restrict who
can see or change a location. Access is instead governed by the application's existing permissions
model through new location-scoped permissions (e.g., `location:view`, `location:add`, `location:edit`,
`location:delete`). The Google Place ID is optional enrichment, and the module stays usable for
viewing/adding/editing saved locations even when Google Maps is unavailable.

## Clarifications

### Session 2026-08-26

- Q: How should the module handle a user entering personal data about a real person into a location's
  name or description? → A: Store the text as the user's own content and show localized guidance
  discouraging entry of others' personal data; no scanning or blocking.
- Q: Should the module cap saved locations per user, and is a location privately owned? → A: No
  per-user cap; locations form a shared collection. The acting user's abstract identity is recorded as
  author (creator) and last editor for auditing only, never to restrict access.
- Q: In the shared collection, who may edit or delete a location? → A: Authorization uses the
  application's existing permissions model with new location-scoped permissions (e.g., `location:view`,
  `location:add`, `location:edit`, `location:delete`); ownership is not used for access control.
- Q: How are concurrent edits to the same location resolved? → A: Optimistic concurrency — detect the
  conflict via a version/last-updated check and reject the stale save with a clear "reload and retry"
  message; never silently discard an update.
- Q: Is the ±500 m proximity match a dedup/uniqueness gate? → A: No. It is an advisory suggestion of
  already-registered nearby places so the user can pick one; adding a new location within 500 m is
  always allowed and the module never blocks or auto-merges duplicates.
- Q: What is the "Add location" flow and coordinate-source order? → A: Click "Add location" → open a
  Google Maps picker to select a Place ID + coordinates or coordinates alone → run the ±500 m proximity
  suggestion → user picks an existing location or proceeds to create a new one. *(Update: the original
  answer added a Telegram coordinate fallback for Telegram-authenticated principals when Maps is
  unavailable; that was dropped — coordinates are now optional, so an unavailable Maps simply yields a
  location without coordinates. Telegram/browser geolocation is used only to centre the map picker.)*

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Suggest existing nearby places (Priority: P1)

Once coordinates are known (from the Google Maps picker), the module suggests
already-registered locations within ±500 m so the user can pick an existing one instead of creating a
duplicate. The suggestion is advisory only: the user may always choose to add a new location, even when
nearby matches exist. This is step 3 of the "Add location" flow (see User Story 3).

**Why this priority**: This proximity suggestion is the main capability the module exists to provide —
helping users reuse a known place. It never blocks or auto-merges; multiple places within 500 m are
allowed by design.

**Independent Test**: With a saved location at known coordinates, query with coordinates ~100 m away
and confirm it is suggested; query with coordinates ~2 km away and confirm it is not; confirm the user
can still add a new location when a suggestion is present.

**Acceptance Scenarios**:

1. **Given** a saved location and input coordinates within 500 m of it, **When** a proximity match runs,
   **Then** that location is suggested so the user can pick it.
2. **Given** input coordinates with no saved location within 500 m, **When** a proximity match runs,
   **Then** no suggestion is shown and the user is offered the option to add a new location.
3. **Given** one or more saved locations within 500 m of the input coordinates, **When** the user
   reviews the suggestions, **Then** they are shown nearest-first and the user may either pick one or
   proceed to add a new location anyway (no restriction on adding within the radius).

---

### User Story 2 - Acquire coordinates via Google Maps (optional) (Priority: P1)

In the "Add location" flow, a Google Maps picker lets the user select a **Place ID with coordinates**
(search or POI tap) or **coordinates alone** (a map point). Coordinates are **optional**: if Google Maps
is unavailable, the user adds the location **without coordinates**. *(Superseded: the original story
acquired coordinates from Telegram for Telegram-authenticated principals; that fallback was dropped —
coordinates are now optional, and Telegram/browser geolocation only centres the map picker.)*

**Why this priority**: Coordinate acquisition is the entry step of registration (User Story 3); its
result (or absence) drives whether the proximity suggestion (User Story 1) runs.

**Independent Test**: In the add flow with Maps available, pick a place → confirm Place ID + coordinates
are captured, and pick a point → confirm coordinates are captured. Then simulate Maps unavailable →
confirm the add form opens with no coordinates and the location can be saved without them.

**Acceptance Scenarios**:

1. **Given** the add flow with Google Maps available, **When** the user picks a place, **Then** a Place
   ID and coordinates are captured; **When** the user picks only a point, **Then** coordinates are
   captured without a Place ID.
2. **Given** Google Maps is unavailable, **When** the user adds a location, **Then** the add form opens
   with no coordinates and the location is saved without them — no hang, no fabricated coordinates.

---

### User Story 3 - Add a location (Priority: P1)

Registration flow: the user clicks **"Add location"** → the module acquires coordinates (User Story 2:
Google Maps picker; optional when Maps is unavailable) → runs the proximity suggestion (User Story 1,
when coordinates were acquired) → the user
either picks a suggested existing location or proceeds to create a new one → confirms a name (and
optional description / Place ID) → saves.

**Why this priority**: Saving places is the foundation of the collection; proximity and name search are
meaningful only once locations exist.

**Independent Test**: Complete the flow from the Add button: acquire coordinates, see any nearby
suggestions, choose "add new", enter a name, save; confirm the location persists across sessions.

**Acceptance Scenarios**:

1. **Given** acquired coordinates and no nearby suggestion (or the user declines suggestions), **When**
   they enter a non-empty name and save, **Then** a new location is stored in the shared collection.
2. **Given** nearby suggestions exist, **When** the user picks one, **Then** no new location is created
   and the existing location is selected/opened instead.
3. **Given** nearby suggestions exist, **When** the user chooses "add new anyway", **Then** a new
   location is created (adding within the radius is allowed).
4. **Given** invalid input (empty name, out-of-range coordinates), **When** the user tries to save,
   **Then** they see clear, localized validation feedback and nothing is saved.

---

### User Story 4 - Search locations by name (Priority: P2)

A user finds a saved location by typing part of its name.

**Why this priority**: Complements coordinate matching for the common case where the user knows the
place by name; useful but secondary to the proximity match.

**Independent Test**: With several saved locations, type a partial name and confirm only matching
locations are shown; clearing the query restores the full collection.

**Acceptance Scenarios**:

1. **Given** multiple saved locations, **When** the user enters a name query, **Then** the collection is
   filtered to locations whose name matches.
2. **Given** a query that matches nothing, **When** results are computed, **Then** a clear, localized
   "no results" state is shown with a way to clear the query.

---

### User Story 5 - Display saved locations (Priority: P2)

A user browses the shared collection of locations and opens any one to see its details, including a map
preview / "open in Google Maps" link when a Google Place ID is present.

**Why this priority**: Viewing is the primary ongoing use once places are saved.

**Independent Test**: With at least one saved location, open the collection, view the list, open a
detail view, and confirm details (and a map preview/link when present) render on a narrow screen.

**Acceptance Scenarios**:

1. **Given** a user with saved locations, **When** they open the module, **Then** their locations are
   listed with enough detail to distinguish them (at minimum name and coordinates/description).
2. **Given** a location with a Google Place ID, **When** the user opens its detail view, **Then** a map
   preview / "open in Google Maps" link (derived from the Place ID and coordinates) is available.
3. **Given** a location whose Google Place ID cannot be resolved, **When** the user opens its detail
   view, **Then** the stored details still display and the missing preview is handled gracefully.
4. **Given** a user with no saved locations, **When** they open the module, **Then** they see a clear,
   localized empty state inviting them to add their first location.

---

### User Story 6 - Edit or remove a location (Priority: P3)

A user updates an existing location (name, description, coordinates, or Google Place ID) or removes a
location they no longer need.

**Why this priority**: Maintenance keeps the collection accurate but is used less often than
recognizing, adding, viewing, and searching.

**Independent Test**: Edit a field of an existing location and confirm the change persists; remove a
location and confirm it disappears from the collection.

**Acceptance Scenarios**:

1. **Given** a user viewing an existing location, **When** they edit a field with valid input and save,
   **Then** the change is persisted and reflected in the collection.
2. **Given** a user editing a location, **When** they provide invalid input, **Then** they see clear,
   localized validation feedback and the change is not saved.
3. **Given** a user choosing to remove a location, **When** they confirm, **Then** it is deleted and the
   user is protected from accidental one-tap deletion.

---

### Edge Cases

- **Multiple nearby matches**: Two or more saved locations fall within ±500 m of the input coordinates —
  all are suggested nearest-first so the user can choose, rather than the system guessing or merging.
- **Add despite suggestions**: Even when one or more locations are suggested within 500 m, the user may
  still add a new location; the module never blocks duplicates within the radius.
- **Just outside the radius**: A saved location slightly beyond 500 m must not be suggested as a match;
  the boundary behavior is deterministic.
- **Google Maps unavailable during add**: The add form opens directly and the location is saved
  **without coordinates** (they are optional) — never a hang or fabricated coordinates (FR-004a).
- **Invalid coordinates**: When coordinates are supplied, values outside valid latitude/longitude ranges
  (or an incomplete lat/lng pair) are rejected with clear, localized feedback.
- **Broken/removed Google Place ID**: A previously linked place no longer resolves upstream — the
  stored location remains valid and usable; the reference is flagged as unresolved.
- **Large collection**: Proximity and name search stay responsive for a large number of saved locations.
- **Concurrent edits**: When two users save edits to the same location concurrently, the module uses
  optimistic concurrency — the second (stale) save is detected via a version/last-updated check and
  rejected with a clear, localized "reload and retry" prompt; no update is silently discarded.
- **Insufficient permission**: A user lacking the required `location:*` permission for an action is
  denied safely with clear, localized feedback, and no partial change is applied.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to add a location consisting of geographic coordinates (required), a
  name (required), a description (optional), and an optional Google Place ID.
- **FR-002**: The system MUST use coordinates as the primary key for proximity suggestions and MUST
  store them with sufficient precision to support them. (Coordinates identify a place for suggestion
  purposes; they do NOT impose a uniqueness constraint — see FR-003a.)
- **FR-003**: Given input coordinates, the system MUST return saved locations located within a
  configurable radius — default **±500 m** — ordered nearest-first as **suggestions**, and MUST return
  an empty result when none fall within the radius. This result is advisory only.
- **FR-003a**: Proximity suggestions MUST NOT block or auto-merge. The system MUST allow the user to add
  a new location even when one or more locations already exist within the radius; there is no per-area
  uniqueness constraint.
- **FR-004**: The "Add location" flow MUST begin by opening a Google Maps component that lets the user
  select either a Place ID together with coordinates, or coordinates alone. The acquired coordinates
  then drive the FR-003 proximity suggestion.
- **FR-004a** *(superseded — coordinates are now optional)*: If Google Maps is unavailable, the system
  MUST let the user add a location **without coordinates** (the add form opens directly; latitude/longitude
  are nullable per data-model.md). It MUST NOT hang or fabricate coordinates. *(The original requirement
  acquired coordinates from Telegram for Telegram-authenticated principals; that fallback was dropped in
  favour of optional coordinates. Telegram/browser geolocation is now used only to centre the map picker,
  never to produce the saved coordinate.)*
- **FR-004b**: After coordinates are acquired, the flow MUST present the FR-003 nearby suggestions and
  let the user either select an existing location or proceed to create a new one (per FR-003a).
- **FR-005**: Users MUST be able to search/filter the shared collection of locations by name (text match).
- **FR-006**: The system MUST display the shared collection of locations as a browsable collection and MUST provide
  a detail view that includes a map preview / "open in Google Maps" link (derived from the Place ID and
  coordinates) when a Place ID is present.
- **FR-007**: Users MUST be able to edit the details of an existing location.
- **FR-008**: Users MUST be able to remove a location, protected against accidental deletion.
- **FR-009**: The system MUST allow a location to optionally store a **Google Place ID** as its
  reference to Google Maps, and MUST treat locations without one as fully valid. The system MUST NOT
  persist a Google Maps URL; when an "open in Google Maps" link or preview is needed, it MUST be derived
  on demand from the stored Place ID together with the location's coordinates (used as the required
  fallback query). An unresolvable Place ID MUST be handled gracefully per FR-013.
- **FR-010**: Locations MUST form a shared collection with no per-user cap and MUST NOT be restricted by
  per-record ownership. Access MUST be governed by the application's existing permissions model via new
  location-scoped permissions — at minimum `location:view`, `location:add`, `location:edit`, and
  `location:delete` — and the system MUST authorize each action against the acting user's granted
  permissions, denying disallowed actions safely without exposing internal details. Each location MUST
  record the abstract user identities of its author (creator) and last editor for auditing only; these
  identities MUST NOT themselves grant or restrict access, and mutations MUST update the audit fields.
- **FR-011**: The system MUST validate location input, including a non-empty name and coordinates within
  valid latitude/longitude ranges, rejecting invalid input with clear, localized feedback.
- **FR-012**: The system MUST persist saved locations in this application's own storage so they remain
  available across sessions independently of Google Maps availability.
- **FR-013**: The system MUST remain usable for proximity suggestion (on already-available coordinates),
  viewing, name search, editing, and removing when Google Maps is unavailable. Adding a brand-new
  location also stays available when Maps is down: it is saved **without coordinates** (they are optional,
  FR-004a); all other capabilities stay functional.
- **FR-014**: Google Maps lookups and previews MUST complete within a finite, configurable timeout or
  resolve to a clear retry/fallback state; the system MUST NOT hang, silently drop the action, or
  fabricate a successful result.
- **FR-015**: The system MUST NOT collect, derive, persist, or expose personal data about natural
  persons; location records MUST be treated as shared business data, with author/editor recorded only as
  abstract user identities and no mapping to a real person stored.
- **FR-016**: All user-facing text (labels, validation, empty/error/retry states, notifications) MUST be
  resolved through the application's internationalization mechanism and MUST NOT reveal internal details.
- **FR-017**: All location flows MUST be designed mobile-first for narrow screens and progressively
  enhanced for wider displays, with accessible controls and adequate touch targets.
- **FR-018**: Free-text fields (name, description) MUST display clear, localized guidance discouraging
  users from entering personal data about other natural persons. The system MUST store the entered text
  as the user's own content and MUST NOT scan, block, or redact it. (Personal data supplied by the user
  into these fields is the user's own responsibility; the app still stores no natural-person mapping.)
- **FR-019**: The system MUST use optimistic concurrency for location edits: each location MUST carry a
  version/last-updated token, and a save based on a stale token MUST be rejected with a clear, localized
  "reload and retry" message. No update may be silently discarded or overwritten.

### Key Entities *(include if feature involves data)*

- **Location**: A place saved in the shared collection. Attributes: coordinates (latitude/longitude —
  primary match key, required, not unique), name (required), description (optional), optional Google Place ID, author
  (abstract user identity of the creator — audit only), last editor (abstract user identity of the most
  recent modifier — audit only), a version/last-updated token (for optimistic concurrency), and
  created/updated timestamps. Not restricted to a single owner.
- **Google Place ID**: An optional, stable identifier pointing to a Location's counterpart on Google
  Maps, used for preview/navigation. It is opaque enrichment data, not an identity or authorization
  input; the user-facing "open in Google Maps" link is derived from it plus the location's coordinates
  and is not stored.
- **Proximity Query**: An input set of coordinates (from an incoming geolocation, or resolved via Google
  Maps) evaluated against saved locations within the configured radius (default ±500 m).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For any input coordinates, the proximity suggestion returns exactly the saved locations
  within the configured radius (default 500 m) and excludes those beyond it, verified with test
  coordinates at ~100 m (suggested) and ~2 km (not suggested). Adding a new location within the radius
  is never blocked.
- **SC-002**: A user can save a new location (coordinates + name) in under 60 seconds and no more than 5
  interaction steps on a mobile screen.
- **SC-003**: Proximity match and name search return correct results effectively instantly from the
  user's perspective (application-owned response, excluding any external Google Maps wait). The module
  imposes no hard per-user cap; responsiveness is validated functionally (not via large-scale load
  testing).
- **SC-004**: All core tasks (proximity match on provided coordinates, view, add, edit, remove, name
  search) can be completed successfully when Google Maps is unavailable, with no data loss and clear
  status feedback.
- **SC-005**: All primary flows are fully usable on a narrow mobile viewport (no horizontal scrolling,
  all actions reachable) and in at least one non-default supported locale.
- **SC-006**: No personal data about natural persons appears in stored records, logs, or diagnostics, as
  verified by review of the module's data and telemetry.

## Assumptions

- Users are authenticated through the existing secure-service flow; the module operates on the abstract
  user identity it provides and stores no natural-person data (aligns with the project constitution).
- Locations form a single shared collection with no per-user cap; the acting user's abstract identity is
  recorded as author/last editor for auditing only and never restricts who can view or change a location.
- Access relies on the application's existing permissions model; this feature adds new location-scoped
  permissions (`location:view`, `location:add`, `location:edit`, `location:delete`). Their exact names
  and granularity are finalized during planning.
- In the "Add location" flow the coordinate source is the Google Maps picker (Place ID + coordinates, or
  coordinates alone). Coordinates are **optional**: when Maps is unavailable the location is added without
  them. *(Superseded: an earlier assumption made Telegram a conditional coordinate fallback for
  Telegram-authenticated principals; dropped in favour of optional coordinates.)* A manual coordinate-entry
  affordance exists as a secondary preview aid but is not a required entry path.
- The map picker best-effort **centres** on the user's location (browser geolocation → Telegram
  `LocationManager` → Kyiv default), feature-detected and non-blocking; a failure just leaves the map on
  the default. This never produces the saved coordinate and introduces no new identity handling.
- "Local storage" means persistence in this application's own backing store (not merely transient
  browser storage), so locations survive across devices and sessions for the same account.
- The ±500 m match radius is a sensible default and SHOULD be configurable rather than hard-coded.
- Google Maps integration is an optional enhancement layered on top of a fully functional local store;
  the module never depends on Google Maps for proximity matching on already-available coordinates, or
  for viewing/editing saved location details.
- Removing a location (delete) is included as part of managing the collection even though only Add/Edit/
  Display/Search were named, because a manageable collection requires removal; it is protected against
  accidental deletion.
- External Google Maps calls are subject to finite timeouts and controlled resource use; there is no
  fixed end-to-end user-latency target for flows whose duration depends on Google Maps.
