# Workspace events

Workspace events are scheduled Padel or Tennis records contained in exactly one workspace. They are
plaintext application content, not personal data, and are not encrypted.

## Capabilities

- Owners browse the active workspace's events, with a title filter and explicit load-more paging. The
  list is ordered by start time, then title, then identifier.
- Owners create, edit, publish or return an event to draft, and delete it after confirmation.
- Publication is a status only. Both drafts and published events are visible to the workspace owner; this
  application currently exposes no public or non-owner event feed.
- Owners register the workspace's known participants against an event, order that roster, and unregister
  them. See [Participant registration](#participant-registration).

## Access and lifecycle

`workspace-event:create` and `workspace-event:list` address the workspace. `read`, `update`, and
`delete` address an event record and resolve through `rg_workspace_event.workspace_unique_id` to the
owning workspace. As for every contained type, ownership grants all operations and local permissions are
the declared resource and action names for future granular access.

The event's workspace is assigned by the create operation, is never editable, and is removed through an
`WorkspaceContentContributor` before the workspace row is deleted. The database foreign key deliberately
has no cascade, so a missing contributor fails visibly instead of deleting content silently.

## Data and presentation

`rg_workspace_event` stores `unique_id`, `workspace_unique_id`, `title`, required `start_at`, optional
`end_at`, required `location_unique_id`, ordinal `event_type`, required positive `max_participant_count`,
`is_published`, `version`, and the standard
audit fields (`author`, `last_editor`, `created_at`, `updated_at`). The type values are `PADEL` (ordinal 0)
and `TENNIS` (ordinal 1); new values must be appended so persisted rows remain stable. Title is required
after trimming and has a 512-character limit, start time, type, and maximum participant count are required,
and the count is greater than zero. An end time cannot precede the start. The selected location must belong
to the event's workspace. The restrictive foreign key prevents deletion while an event references the
location. Updates carry the read version and reject stale writes.

`WorkspaceEventsView` is `/workspaces/events` within `WorkspaceLayout`. A user with
`workspace-event:create` sees Browse and Add tabs; otherwise Browse is rendered directly without tab
captions. It follows the shared mobile-first disclosure-list shape: title filter, expanded-row outlined
management actions, edit dialog, and confirmed removal. After successful creation, Browse is selected, filtered by the saved
title, and smoothly scrolled to the new row. The add and edit forms use a responsive `FormLayout` with
title, required start time, optional end time, a required workspace-scoped `LocationPicker`, required event type,
required maximum participant count, and publication fields. The date-time fields are direct localized
`DateTimePicker` controls created with the shared temporal-component `DateDisplayOptions`. Each field keeps
its date and time inputs on one line and shows the localized short weekday beside its label. New events start
at the current UTC date at 12:00; the optional end can be cleared. Participant capacity initializes to 24.
Invalid fields receive focus and retain their error state until edited. Persisted `Instant` values convert
explicitly to and from UTC local input.

Each browse row's caption carries the event title with the registered-over-maximum count as a
right-aligned marker on the same line (e.g. `3/24`), and a secondary line of the start time — short
weekday, date and `HH:MM`, e.g. `Wed Sep 24 · 14:30`. The date is formatted through
`LocalizationService.formatEventDateTime`, which bridges to the shared `component.datetime` formatter so
the caption and the detail rows share one presentation (see
[dates-and-times.md](./dates-and-times.md)). The registered counts for a page are read in a single
query, guarded by `workspace-event:list`, and the list is refreshed when the participants dialog closes
so the counts reflect any registrations made while it was open.

## Participant registration

An event registration joins one event to one of the workspace's participants, ordered within the event.
It is workspace content like the event itself; the owner's authority over it comes from owning the
workspace, so it introduced no new authority rule.

`rg_workspace_event_registration` stores `unique_id`, `event_unique_id`, `participant_unique_id`, an
`order_by` position, `version`, and the standard audit fields. It deliberately does **not** store the
workspace: unlike a direct child of the workspace (event, participant, location), a join row's workspace
is derivable, so everything workspace-wide resolves by joining through the event. A unique constraint on
`(event_unique_id, participant_unique_id)` keeps a participant registered to an event at most once.
Foreign keys to the event and participant are restrictive (no cascade): the event and participant
services remove an item's registrations before deleting it, and a dedicated `WorkspaceContentContributor`
removes a workspace's registrations (a bulk delete scoped by the event join) on workspace deletion —
ordered ahead of the event, participant and location contributors, since a registration references the
first two.

`WorkspaceEventRegistrationService` registers, lists, reorders (move up / move down, swapping adjacent
`order_by` values) and unregisters. Registration appends last, validates the participant belongs to the
event's workspace, and rejects a duplicate; **capacity is not enforced — an event may be over-booked past
its maximum deliberately.** The roster is returned ordered by `order_by`, each entry carrying the
participant's label resolved at read time (never a phone number). `register` and `list` are addressed by
the event (`workspace-event:manage-participants`); `unregister` and the reorder verbs are addressed by
the registration (`workspace-event-registration:delete` / `:reorder`) and resolve to the owning workspace
through a dedicated scope provider that joins registration → event → workspace.

The owner reaches this from a **Participants** action right-aligned at the very top of the event's
expanded detail panel, above the detail rows — which opens `EventParticipantsDialog` (full width),
no route or URL parameter since the event is held in memory. It closes from an X in the dialog header
corner (no footer button). Inside, a two-tab sheet separates a **Registered {count}** tab from a
**Register** tab; the registered tab's caption carries the live roster count.

Both tabs share the same shape: a `ScrollCue` (the shared scroll-cue component, see
[Shared components](workspace.md#modules)) — a scrollable list of rows in its own viewport that fades in
an up chevron while there is content scrolled above and a down chevron while there is content below
(toggled on the client from the viewport's scroll position and size). Both tabs' rows are built from one
shared row helper: a name that grows and wraps on one line with trailing icon actions pinned to the
edge, and a little more vertical spacing between rows.

On the registered tab the row actions are move-up, move-down and unregister, the reorder buttons
disabled at the ends; after a move the viewport's scroll shifts by one row (via `ScrollCue.scrollByRows`)
so the moved row — and the button just tapped — stays at the same screen position, letting repeated moves
land on the same spot. The
Register tab searches the workspace's participants, excluding those already registered, each row showing
the name with a trailing `+` action. Pressing `+` registers the person and keeps the Register tab
selected: the just-registered row collapses and fades out before being removed, and the registered tab's
roster and count refresh behind it. It is mobile-first: a single column of rows throughout.
