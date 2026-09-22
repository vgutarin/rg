# Participants

## Purpose and actors

Owners register and manage people. An unbound participant is a local record. A
participant bound to an application identity is a society member for event visibility,
not a workspace manager.
[Participant logic](../logic/workspace/participants.md) owns identity, validation,
contact exposure, and persistence.

## Routes and entry conditions

`/workspaces/participants` runs inside the active [workspace layout](workspaces.md)
and requires its owner gate and the participant-list scope check.

The scoped create check exposes Browse and Add tabs; without it the screen renders
Browse directly. The roster uses a filter and [shared disclosure rows](shared-interactions.md#disclosure-lists).
Owners also receive a Requests tab. It lists pending Join Society requests by the label from an encrypted
requester descriptor; no phone is rendered. Approve chooses an explicitly selected unbound participant or creates
one; choosing an existing participant loads the first 50 eligible records in label and
identifier order. Typed ComboBox text is sent as a server-side label filter, while the result remains capped at 50.
A missing or configured-limit-exceeding
requester label requires an owner-provided label. Approve or Reject removes the request from the
queue; a later request can be submitted.

## Interactions and outcomes

### Browse and register

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

### Contact actions

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

### Edit and remove

Edit pre-fills the label. It fetches and pre-fills the phone only after the scoped
reveal-contact check passes; opening that form is a disclosure. Otherwise the field
is empty and its helper warns that saving replaces the contact descriptor, including
the phone.

Removal has a confirmation step and protection against a repeated press. Successful
changes refresh the list; refresh failure is handled separately from persistence
success so a committed operation is not reported as a failed write.

## Functional requirements

- **FR-001**: Browse MUST request the active workspace's ordered roster in prefixes of 50, and filter changes MUST reset the loaded prefix.
- **FR-002**: A roster row MUST show an accessible phone-presence glyph only when a phone is recorded, without digits or a masked suffix.
- **FR-003**: Show phone MUST call the guarded contact service and display the result in a dialog with an accessible Copy action when a number exists.
- **FR-004**: The edit form MUST fetch a saved phone only after its reveal-contact check passes; otherwise it MUST explain replacement semantics beside an empty phone field.
- **FR-005**: Successful registration MUST select Browse, filter by the saved label, and request scrolling to the saved row.
- **FR-006**: Membership approval using an existing participant MUST query unbound candidates with the typed label filter and cap displayed results at 50.
- **FR-007**: Copy feedback MUST follow the browser's clipboard result: success requests pulse/haptics and failure shows localized manual-copy guidance.

## States and failures

Empty rosters and unmatched filters have distinct messages. Registration validation,
duplicate-phone, and limit failures keep typed input. Stale saves retain the draft
with reload/retry guidance. A missing revealed number has no Copy action. Copy
failure messages contain neither the phone nor browser diagnostic text.

## Presentation and platform constraints

[Foundations](foundations.md) govern wrapping, touch targets, action styling, and
localization. Contact-number normalization and replacement semantics remain in logic.

## Invariants

- **INV-001**: Rendering the roster does not call `revealContact` (checked by `WorkspaceParticipantsViewTest`).
- **INV-002**: Without reveal permission, the row offers no Show phone action and the edit form fetches no phone (checked by `WorkspaceParticipantsViewTest`).
- **INV-003**: A repeated confirmation press causes at most one participant deletion (checked by `WorkspaceParticipantsViewTest`).

## Success criteria

- **SC-001**: Successful registration displays the new participant on Browse with its saved-label filter (`WorkspaceParticipantsViewTest`).
- **SC-002**: Load more retains the first page and extends the requested prefix by 50 rows; changing the filter returns to the first prefix (`WorkspaceParticipantsViewTest`).
- **SC-003**: A failed clipboard result produces safe localized guidance, and success animation is requested only after a successful browser result (`WorkspaceParticipantsViewTest`; device acceptance is tracked separately).

## Related contracts

- [Participant logic](../logic/workspace/participants.md), [workspaces](workspaces.md), and [shared disclosure lists](shared-interactions.md#disclosure-lists).

## Evidence

- [WorkspaceParticipantsView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceParticipantsView.java)
- [WorkspaceParticipantsViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceParticipantsViewTest.java)
