# Event registrations

## Responsibility and model

A registration joins an event to a workspace participant and has its own identifier,
position (`order_by`), version, and audit fields. It stores event/participant identifiers,
not workspace identity; scope resolves through registration → event → workspace.

The unique constraint on `(event_unique_id, participant_unique_id)` prevents duplicate
membership in the same event. Event and participant foreign keys are restrictive.
This relationship grants the participant no workspace authority.

## Commands and queries

| Operation | Address and result |
| --- | --- |
| Register | Event with manage-participants; require participant in the event workspace, reject duplicate, append at max position + 1 (zero for empty roster) |
| List | Event with manage-participants; return positions ascending, then registration identifier |
| Unregister | Registration with delete; remove its row |
| Move up/down | Registration with reorder; swap positions with its neighbor, no-op at corresponding end |
| Count by events | Workspace with event-list; grouped counts for supplied event identifiers, omitting zero-count events |

Registration deliberately permits overbooking above the event's configured maximum.
Invalid cross-workspace participants and duplicates return stable localization keys.
Unknown records raise entity-not-found where reached.

List resolves labels from the event workspace's participant descriptors at read time;
returned registration models contain labels, not phone numbers. The participant scan
is bounded operationally by the workspace roster limit.

The count operation checks authority on its supplied workspace, but its repository
query filters only by supplied event identifiers, not by that workspace. Its UI caller
supplies identifiers from the scoped event page. The service does not independently
validate that every identifier belongs to the authorized workspace.

## Consistency and lifecycle

Register, unregister, and reorder are transactional service operations. Reorder writes
both neighboring records. Positions can have gaps after removal; the query includes
identifier tie-breaking. The uniqueness constraint protects event/participant pairs,
not position values; append uses a maximum-position read rather than an event-wide lock.

Deleting a participant or event first deletes its registrations. Workspace removal
uses a registration contributor ordered ahead of events, participants, and locations;
workspace bulk deletion scopes by an event join.

## Interactions

[Participants](participants.md) supply local identity and labels.
[Events](events.md) supply schedule, capacity, and scope.
[Event UI](../../ui/events.md#event-participants-dialog) owns ordering controls, tab
behavior, scrolling, and displayed counts.

## Evidence

- [WorkspaceEventRegistrationServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/event/WorkspaceEventRegistrationServiceImpl.java)
- [WorkspaceEventRegistrationRepository.java](../../../../rg-logic/src/main/java/vg/rg/repository/workspace/WorkspaceEventRegistrationRepository.java)
- [EventRegistrationWorkspaceContentContributor.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/event/EventRegistrationWorkspaceContentContributor.java)
- [006-workspace-event-registration.yaml](../../../../rg-logic/src/main/resources/db/liquibase/006-workspace-event-registration.yaml)
- [WorkspaceEventRegistrationServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventRegistrationServiceImplTest.java)
- [WorkspaceEventRegistrationServiceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventRegistrationServiceFuncTest.java)
