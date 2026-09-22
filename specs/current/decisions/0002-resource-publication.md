# ADR-0002: Resource publication

| Field | Value |
| --- | --- |
| Status | Accepted |
| Created | 2026-09-29 |
| Decision date | 2026-09-29 |
| Decision makers | Product and engineering |
| Supersedes | None |
| Superseded by | None |

## Context

Events are the first content type needing a publication instant and typed audience
rules, but later resource types need the same capability. Keeping these records on
the event table would duplicate publication lifecycle and audience persistence.

## Alternatives

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Resource-keyed publication and rules | Reusable state and one rule representation | Resource lifecycle must explicitly clean up generic rows |
| Event-local publication columns and rules | Simpler event mapping | Duplicates schema and behavior for each future resource |
| One polymorphic resource foreign key | Referential integrity | Cannot target multiple resource tables with a relational FK |

## Decision

Publication uses `rg_resource_publication`, keyed by resource unique ID, with a
publication timestamp, visibility mode, optimistic version, and audit metadata.
Rules use `rg_resource_visibility_rule` and reference the publication, not an event.
No generic resource foreign key exists; each resource lifecycle deletes its own
publication. A null publication instant is a draft; publishing records the current UTC
instant and unpublishing clears it.

Events keep their current service and UI policy vocabulary through an adapter. Event
audience evaluation remains responsible for resolving workspace membership targets.

## Consequences

- A future resource can use the generic persistence layer without another publication schema.
- Publication updates have independent optimistic concurrency from event-content edits.
- Event deletion and workspace cleanup must delete resource publication state explicitly.
- This refines the persistence boundary in ADR-0001 without changing its audience or
  membership decision, so ADR-0001 remains accepted.

## Related contracts and evidence

- [Resource publication logic](../logic/resource-publication.md)
- [Event logic](../logic/workspace/events.md)
- [ADR-0001](0001-event-visibility-and-membership.md)
