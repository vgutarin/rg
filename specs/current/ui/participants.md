# Participants

## Purpose and entry

Owners register and manage people at `/workspaces/participants`, inside the active
[workspace layout](workspaces.md). A participant is a record, not a workspace member.
[Participant logic](../logic/workspace/participants.md) owns identity, validation,
contact exposure, and persistence.

The scoped create check exposes Browse and Add tabs; without it the screen renders
Browse directly. The roster uses a filter and [shared disclosure rows](shared-interactions.md#disclosure-lists).

## Browse and register

Browse is ordered by label and identifier using the domain service. Paging is explicit
Load more in increments of 50 with a showing-X-of-Y indicator, not infinite scroll. Each load requests the
entire loaded prefix in one service call so the displayed list is one ordered snapshot.
Filter changes reset the loaded prefix. Empty rosters and unmatched filters have
localized states.

A row has an accessible phone glyph only when a number exists; it shows neither
digits nor a masked suffix. No-number rows omit the glyph.

Registration accepts a label and optional phone. Duplicate numbers, count limits,
and validation failures have localized feedback. After success, Browse is selected,
filtered by the saved label, and smoothly scrolled to the new row.

## Contact actions

The expanded row offers Show phone when contact disclosure is allowed and the model
reports a phone. No Call control is rendered in the participant list or contact dialog.

- Show phone retrieves contact data through `revealContact` and opens a dialog with
  the number, accessible Copy-number icon, and Close. There is no Call action in it.
- A missing number produces a localized message and no copy action.

Copy uses Vaadin 25.2 `Clipboard.onClick(...).writeText(...)`, binding the write to the
browser gesture. Success pulses the number and requests Telegram success haptics when
available; reduced-motion preference suppresses the animation. Failure displays a
localized manual-copy hint without including the number or browser error. Contact data
and clipboard errors are not logged by these view actions.

## Edit, remove, and failures

Edit pre-fills the label. It fetches and pre-fills the phone only after the scoped
reveal-contact check passes; opening that form is a disclosure. Otherwise the field
is empty and its helper warns that saving replaces the contact descriptor, including
the phone. Stale saves retain the draft with reload/retry guidance.

Removal has a confirmation step and protection against a repeated press. Successful
changes refresh the list; refresh failure is handled separately from persistence
success so a committed operation is not reported as a failed write.

[Foundations](foundations.md) govern wrapping, touch targets, action styling, and
localization. Contact-number normalization and replacement semantics remain in logic.

## Evidence

- [WorkspaceParticipantsView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceParticipantsView.java)
- [WorkspaceParticipantsViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceParticipantsViewTest.java)
