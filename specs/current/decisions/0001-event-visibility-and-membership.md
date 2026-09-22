# ADR-0001: Event visibility and membership requests

| Field | Value |
| --- | --- |
| Status | Accepted |
| Created | 2026-09-22 |
| Decision date | 2026-09-22 |
| Decision makers | Product and engineering |
| Supersedes | None |
| Superseded by | None |

## Context

Published workspace events need a shareable audience without granting access to the
workspace. The audience must later accommodate individual, workspace, and group
targets, while membership remains a deliberate owner-approved binding rather than
name or contact inference.

## Alternatives

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Typed event-local allow/deny rules with participant bindings as membership | Supports future subject kinds and keeps sharing separate from management | Requires audience evaluation and deletion coordination |
| Workspace-only visibility flag | Small implementation | Cannot express user exceptions, deny rules, or future groups |
| Implicit matching by requester name/contact | Appears convenient | Can bind the wrong person and exposes personal-data assumptions |

## Decision

Workspace events have a `PUBLIC` or `RESTRICTED` visibility policy. A restricted
policy contains typed allow/deny rules for users, workspaces, and reserved future
workspace groups. A matching deny always wins. Public events have no exception
rules. Publication is separate: only published events are disclosed to non-owners.

A bound `workspace_participant.user_unique_id` is workspace membership. People
request that binding from a visible event; the owner approves or rejects in the
Community screen. Requester descriptors are stored encrypted. Approval either binds an
explicitly selected unbound participant or creates a new member record. The system
does not infer identity matches from names or contact details.

## Consequences

- Owners retain complete access to events in their workspace, including drafts.
- The Home feed is an audience boundary, not workspace navigation or management.
- Visibility targets remain polymorphic and therefore have no target foreign key;
  unresolved targets simply do not match.
- Workspace groups have a stable rule kind now but cannot be configured until group
  membership exists.

## Related contracts and evidence

- [Event logic](../logic/workspace/events.md)
- [Participant logic](../logic/workspace/participants.md)
- [Home/events UI](../ui/events.md)
- [Participant UI](../ui/participants.md)
- [Encryption guidance](../engineering/encryption.md)

## Resolution and supersession

Accepted on 2026-09-22 with the implementation and current-state contracts linked
above. No supersession has been recorded.
