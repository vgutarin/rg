# Workspace events

Workspace events are scheduled Padel or Tennis records contained in exactly one workspace. They are
plaintext application content, not personal data, and are not encrypted.

## Capabilities

- Owners browse the active workspace's events by title, with a title filter and explicit load-more paging.
- Owners create, edit, publish or return an event to draft, and delete it after confirmation.
- Publication is a status only. Both drafts and published events are visible to the workspace owner; this
  application currently exposes no public or non-owner event feed.

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
