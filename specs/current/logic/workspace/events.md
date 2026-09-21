# Workspace events

## Responsibility and model

An event is a scheduled Padel/Tennis record, not a DDD domain event. It belongs to one
workspace and is plaintext application content. Its model contains identity, title,
start/end instants, required location identifier, event type, maximum participant count,
publication flag, version, and audit fields.

`rg_workspace_event` stores immutable required workspace identity. `WorkspaceEventType`
persists ordinal values `PADEL=0` and `TENNIS=1`. The referenced location belongs to
the same workspace and is protected by a restrictive foreign key.

## Commands and queries

Create assigns scope from its workspace argument. Update resolves the existing row,
checks its version, and replaces editable event fields without changing scope.
Delete removes registrations before deleting the event.

Browse takes workspace, optional title filter, and pageable. It trims the filter and
uses case-insensitive title containment when nonblank. Paging/order are supplied by
the caller; [events UI](../../ui/events.md) owns the screen's start/title/id order.

Create/list address the workspace; update/delete address the event. The declared
read permission is part of the event permission family, while the service interface
exposes create, browse, update, and delete. [Workspace policy](README.md#authorization-scope)
governs authority.

## Invariants and consistency

- Trimmed title is required and at most 512 characters.
- Start, event type, and maximum participant count are required; the maximum is positive.
- End is optional and cannot precede start.
- Location is required and must exist in this workspace.
- Publication is a boolean status. Draft and published events remain owner-visible;
  there is no public/non-owner feed in this application.
- Updates use the read version and JPA optimistic concurrency.
- Writes are transactional. Workspace removal removes event registrations before
  events and events before their referenced locations.
- Maximum participant count is descriptive capacity, not an enforced registration cap;
  [registrations](registrations.md) can exceed it.

Validation failures carry stable `workspace.event.error.*` keys, including required,
length, schedule, capacity, and location-scope failures. Missing records and stale
writes surface as entity-not-found and optimistic-lock failures where reached.

Instants are persisted independently of locale. Explicit UTC editing/formatting is
the UI contract, not an implicit timezone inferred by the business service.

## Evidence

- [WorkspaceEventServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/event/WorkspaceEventServiceImpl.java)
- [WorkspaceEventEntity.java](../../../../rg-logic/src/main/java/vg/rg/entity/workspace/WorkspaceEventEntity.java)
- [005-workspace-event.yaml](../../../../rg-logic/src/main/resources/db/liquibase/005-workspace-event.yaml)
- [WorkspaceEventServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventServiceImplTest.java)
- [WorkspaceEventServiceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventServiceFuncTest.java)
- [WorkspaceEventSchemaTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceEventSchemaTest.java)
