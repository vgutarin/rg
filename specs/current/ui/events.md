# Events

## Purpose and entry

Owners schedule Padel and Tennis events at `/workspaces/events`, within the active
[workspace layout](workspaces.md). Business rules and publication meaning belong to
[event logic](../logic/workspace/events.md); event rosters belong to
[registration logic](../logic/workspace/registrations.md).

A scoped create check exposes Browse and Add tabs; otherwise Browse renders without
tab captions. Draft and published records are visible to their workspace owner.

## Browse and management

The title filter and explicit Load more request an increasing prefix in groups of 20,
ordered by start time, title, then identifier. Empty lists and unmatched filters have
localized states. Rows use the shared disclosure component and bordered-theme management
actions, with edit dialogs and confirmed deletion.

A row shows its title and, when roster management is available, a right-aligned
registered/maximum marker. Its secondary line contains the localized short weekday,
date, and time through `LocalizationService.formatEventDateTime`. Page counts are
requested together, not once per row; an absent count displays as zero.

## Forms and states

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
the new row into view. Publication changes status, not public visibility.

## Event participants dialog

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

## Evidence

- [WorkspaceEventsView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceEventsView.java)
- [EventParticipantsDialog.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/EventParticipantsDialog.java)
- [WorkspaceEventsViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceEventsViewTest.java)
- [EventParticipantsDialogTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/EventParticipantsDialogTest.java)
