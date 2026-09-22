# Events

## Purpose and actors

Owners schedule Padel and Tennis events. Business rules and publication meaning belong to
[event logic](../logic/workspace/events.md); event rosters belong to
[registration logic](../logic/workspace/registrations.md).

## Routes and entry conditions

`/workspaces/events` runs within the active [workspace layout](workspaces.md) and
requires its owner gate and event-list scope check.

A scoped create check exposes Browse and Add tabs; otherwise Browse renders without
tab captions. Draft and published records are visible to their workspace owner. The
expanded owner details panel contains an immediate Public / Society-only visibility
control. New events default to Public. Society-only writes a restricted policy that
allows members of the event’s workspace; advanced allow/deny policies have no UI here.

## Interactions and outcomes

### Browse and management

The title filter and explicit Load more request an increasing prefix in groups of 20,
ordered by start time, title, then identifier. Empty lists and unmatched filters have
localized states. Rows use the shared disclosure component and bordered-theme management
actions, with edit dialogs and confirmed deletion.

A row shows its title and a right-aligned registered/maximum marker. Its secondary
line contains the localized short weekday,
date, and time through `LocalizationService.formatEventDateTime`. Page counts are
requested together, not once per row; an absent count displays as zero.

### Forms

The responsive `FormLayout` contains title, required start, optional end, required
workspace-scoped location picker, required event type, required positive participant
maximum, and publication checkbox.

New forms initialize both start and end to the current UTC date at 12:00, maximum to
24, and publication to false. End is optional and clearable. Event type and location
require selection. Editing restores the saved values.

Direct localized date-time controls keep date and time on one line and display the
short weekday beside the label. Input conversion uses explicit UTC. Shared input
behavior belongs to [temporal controls](shared-interactions.md#values-and-formatting).

Invalid fields are focused and retain an error until edited; changing start clears
the end-field error. Stale updates show reload/retry guidance without closing the
edit dialog. Creation success selects Browse, filters by the saved title, and scrolls
the new row into view. Publication changes status. Only a published event is
disclosed to a non-owner, regardless of its visibility policy.

### Event participants dialog

A Participants action at the top-right of the expanded detail opens a full-width
dialog. The event is held in memory, not in a route or URL parameter. The header X
closes it; there is no footer close button. Closing refreshes event counts.

Two tabs contain [scroll-cue lists](shared-interactions.md#scroll-cues):

- Registered {count}: name rows with move-up, move-down, and unregister icons.
  Boundary move buttons are disabled. After a move, the viewport shifts by one row
  to keep the moved row and tapped control at the same screen position.
- Register: search among workspace participants, excluding those already registered,
  with a trailing + action. Successful registration keeps this tab selected while
  the added row collapses/fades out and the registered roster/count refreshes.

Both tabs use one row helper with wrapping names and trailing pinned icon actions,
in a single column. Domain registration allows counts above the event maximum.
[Foundations](foundations.md) govern localization and shared presentation.

## Functional requirements

- **FR-001**: Browse MUST request prefixes of 20 events ordered by start, title, and identifier within the active workspace.
- **FR-002**: The event form MUST edit title, UTC start/end, a workspace location, event type, capacity, and publication state using the defaults described above.
- **FR-003**: Successful creation MUST select Browse, filter by the saved title, and request scrolling to the created event.
- **FR-004**: Society-only selection MUST submit a restricted policy allowing the active workspace's members; Public selection MUST submit a public policy without rules.
- **FR-005**: Participants MUST open an in-memory event dialog only when the event's roster-management check passes.
- **FR-006**: Closing the roster dialog MUST refresh event rows and their grouped counts.
- **FR-007**: Registering a participant MUST retain the Register tab while refreshing the registered roster and removing the added candidate from available choices.

## States and failures

Empty lists and unmatched filters use distinct localized messages. Invalid form fields
retain an error until edited; a stale edit remains open. A refused visibility change
restores the displayed policy. Missing or failed grouped-count lookup displays zero;
when count access is unavailable, the lookup is skipped and the marker still shows zero.

## Presentation and platform constraints

The form expands from a narrow layout using Vaadin `FormLayout`. Date/time entry and
summaries use explicit UTC and the shared localized temporal controls. The roster
dialog uses one-column rows with pinned icon actions and scroll cues.

## Invariants

- **INV-001**: The roster's boundary move buttons are disabled (checked by `EventParticipantsDialogTest`).
- **INV-002**: Already registered participants are absent from Register choices (checked by `EventParticipantsDialogTest`).
- **INV-003**: Without roster-management permission, the Participants button is absent (checked by `WorkspaceEventsViewTest`).

## Success criteria

- **SC-001**: Successful creation displays the saved event on Browse with its saved-title filter (`WorkspaceEventsViewTest`).
- **SC-002**: Closing Participants updates the list's registered counts (`WorkspaceEventsViewTest`).
- **SC-003**: Registering a candidate calls the registration service with the selected event and participant identifiers (`EventParticipantsDialogTest`).

## Related contracts

- [Event logic](../logic/workspace/events.md), [registration logic](../logic/workspace/registrations.md), and [resource publication](../logic/resource-publication.md).
- [Workspaces](workspaces.md) and [shared interactions](shared-interactions.md).

## Evidence

- [WorkspaceEventsView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceEventsView.java)
- [EventParticipantsDialog.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/EventParticipantsDialog.java)
- [WorkspaceEventsViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceEventsViewTest.java)
- [EventParticipantsDialogTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/EventParticipantsDialogTest.java)
