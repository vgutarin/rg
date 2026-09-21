# Domain history and design rationale

This record preserves historical explanations extracted from the flat specifications.
It is not a current behavior contract; those live in [UI](../ui/README.md) and
[logic](../logic/README.md).

## Global location scope

The original `rg_location` collection was shared by permission holders. Feature
[003](../../003-workspace-layer/spec.md) replaced it with per-workspace locations,
using the earlier [geolocation feature](../../002-geolocation-module/spec.md) as context.

A one-time startup transaction copied every shared location into each author's
system-named default workspace: A authors × L locations. Copying only authored rows
would have removed places from a collection previously shared by everyone. The
transaction wrote `GLOBAL_LOCATION_TO_WORKSPACE` into `rg_migration_marker` last.

Completion in the deployed environment was recorded as manually verified on
2026-09-03. The migration code was then removed. An environment that never ran that
startup path does not acquire those copied rows automatically.

Two tables remain retained and unmapped: `rg_location` as the copy's rollback source,
and `rg_migration_marker` as completion evidence. The latter has migration-agnostic
`name`/`completed_at` columns. Removal of the former is [open decision 2](../open-decisions.md#2-drop-rg_location--unblocked-not-urgent).
The architecture tests guard against mapping the retired location collection again.

Historical workspace requirements, design, and operational walkthroughs remain in
[feature 003](../../003-workspace-layer/plan.md). Branch history has been rewritten,
so some short hashes quoted in older documents ceased to identify reachable commits.

## Participant identity

Participant registration was designed in conversation rather than in a numbered
feature directory. It needed to represent people who might never authenticate, so
a participant could not be modeled merely as a pending platform user.

The local participant identifier was chosen as the only reference from application
content. The optional identity-subject field is separate, so identity binding need
not rewrite event or other content references. An invitation redemption flow was
discussed but remains [open decision 6](../open-decisions.md#6-let-an-invited-participant-actually-bind-to-an-identity--blocked-on-a-consent-step).

Registration intentionally does not ask identity whether a phone belongs to an
existing account: lookup-and-reuse would expose a phone-number membership oracle.
Duplicate checks within an owner's own workspace do not need that cross-platform lookup.

The `workspace-participant` permission resource name avoided claiming the generic
`participant` noun for every possible kind of participation. The separate
reveal-contact capability names the plaintext-disclosure boundary even though the
single-owner policy grants all local operations.

A phone-presence glyph was chosen instead of digits or a masked suffix. The absence
of a number needs no disabled icon or caption; the presence marker has an accessible
name. Opening an edit form pre-fills via reveal-contact because a blank full-descriptor
replacement would otherwise erase the number on a label-only edit. The helper for
editing without reveal permission explains that replacement risk.

## Contact storage and encryption

Identity claims were rejected as participant storage because an unauthenticated
person may have no identity row. A generic identity-service `POST /secure-data/`
vault was also rejected: it introduced an untyped service boundary and still required
fetching the data back for application rendering.

The application therefore owns typed participant contact data, encrypted at rest,
under the narrow allowance recorded in constitution 4.0.0. This refactor does not
amend that allowance or the constitution.

One encrypted descriptor was chosen over separate encrypted label/phone columns.
Neither design permits plaintext SQL search/order/constraints; splitting the fields
would add envelopes, wiring, and migrations without those benefits. JSON wire names
and a payload schema version preserve meaning independently of Java component names;
unknown-field tolerance and omitted nulls support compatible reads and compact storage.
Redacted string representations prevent incidental object logging from exposing contacts.

Field encryption was copied from the identity-service project rather than shared as
a library. The copies use independent keys and do not decrypt each other's data.
Sharing was deferred unless more consumers or cross-service data exchange justify it.
The identity service's keyed blind index was not copied: the bounded participant
roster supports in-memory decryption, duplicate detection, sorting, and filtering.

The sealed envelope has no format-version discriminator, so the participant payload
carries its own schema version. The participant converter also provided a production
entity that exercises Hibernate's Spring-managed converter resolution; generic converter
wiring tests alone could not prove that path. Raw-column assertions distinguish actual
encryption from a plaintext round trip.

Ciphertext was not bound to a row through AEAD additional data. A writer capable of
moving blobs can also rewrite plaintext owner/scope columns; a blob-only consistency
check does not establish database-write integrity. Row binding would also affect
partial restores or copied rows and require changing the pinned encryption contract.
The threat model protects readers of stored data, not application compromise,
arbitrary database writes, or erasure from existing backups.

## Removal through contributors

Restrictive foreign keys and explicit contributors were chosen instead of relying
on database cascades. A forgotten contributor then causes a visible constraint
failure rather than silently deleting content.

Service removal also handles active-selection repair, default provisioning, and
confirmation semantics. Database cascades do not invoke entity auditing and can leave
loaded JPA entities stale; they cannot perform external cleanup or refuse a domain
operation. Contributors retain a seam for those behaviors. A database-level orphan
protection policy would be a separate decision, not a replacement for contributors.

Registration stores no workspace column because its event already supplies that
scope. Deletion order follows the restrictive dependencies: registrations before
events/participants, events before locations, all before the workspace.

## Shared UI and paging choices

DisclosureList was shared by locations and participants to keep row DOM, accessibility,
open-state behavior, and styles together rather than relying on conventions. Shared
class constants avoid silent cross-file CSS spelling mismatches. Identity-keyed open
state preserves a renamed row even when it sorts elsewhere.

Participant paging uses an explicit count and Load more because a silently truncated
alphabetical roster looks complete. Encrypted labels already require a full scan, so
requesting the full loaded prefix yields a consistent snapshot without additional
scan savings from fetching only the last page.

A Grid alternative was considered but deferred: moving one screen would split the
shared presentation, definite-height Grid scrolling would nest inside a Telegram
page, and lazily loaded components were affected by the cached production-bundle
incident. Deployment verification remains [open decision 1](../open-decisions.md#1-verify-the-production-frontend-bundle-in-the-deployed-environment--status-changed).

Workspace content entries were placed at the top navigation level because workspace
is their scope rather than an intermediate destination. Independent scoped checks
align visible entries with service authority; resolving the active workspace once
avoids repeated provisioning work. Workspace management's omitted navigation entry
is tracked separately from the working routes.

System-created workspaces store a null name so the UI can translate the label for
the viewer. User-entered text is kept verbatim. Workspace selection belongs to a
nested layout so it is absent outside workspace routes.
