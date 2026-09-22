# Workspace events

## Responsibility and model

An event is a scheduled Padel/Tennis record, not a DDD domain event. It belongs to one
workspace and is plaintext application content. Its model contains identity, title,
start/end instants, required location identifier, event type, maximum participant count,
event version, and audit fields. Its publication state and visibility policy are
owned by the linked [resource publication](../resource-publication.md).

`rg_workspace_event` stores immutable required workspace identity. Persisted
`WorkspaceEventType` ordinals are `PADEL=0`/`TENNIS=1`; new values are appended,
never inserted or reordered. The referenced location belongs to the same workspace
and is protected by a restrictive foreign key.

## Commands and queries

Create assigns scope from its workspace argument. Update resolves the existing row,
checks its version, and replaces editable event fields without changing scope.
Delete removes registrations before deleting the event.

`replaceVisibilityPolicy` is an owner-only, publication-version optimistic-locking command. `PUBLIC`
policies have no rules. `RESTRICTED` policies contain typed `ALLOW`/`DENY` rules
for user and workspace subjects; the reserved group kind is persisted but refused by
the event service. A target is matched without a polymorphic foreign key: a user target
matches that identity, and a workspace target matches a bound participant member of
that workspace. A matching deny wins.

Browse takes workspace, optional title filter, and pageable. It trims the filter and
uses case-insensitive title containment when nonblank. Paging/order are supplied by
the caller; [events UI](../../ui/events.md) owns the screen's start/title/id order.

Home discovery evaluates its audience predicate in the database before paging: authenticated
viewers receive only published public events or published restricted events with a
matching allow and no matching deny. Ownership does not bypass this audience query;
owner management uses the separately authorized workspace browse operation. A
provisional viewer has no identity matches and therefore receives only published public
events. The query orders by start, title, and identifier.

Create/list address the workspace; update/delete address the event. The declared
read permission is part of the event permission family, while the service interface
exposes create, browse, update, and delete. [Workspace policy](README.md#authorization-scope)
governs authority.

## Functional requirements

- **FR-001**: Create/update MUST require a trimmed title of 1–512 characters.
- **FR-002**: Create/update MUST require a start instant, event type, and positive maximum participant count.
- **FR-003**: An optional end instant MUST NOT precede the start instant.
- **FR-004**: The selected location MUST exist in the event's workspace.
- **FR-005**: Update MUST reject a stale event version; publication changes MUST use the separate publication version.
- **FR-006**: Owner browse MUST include both drafts and published events in the addressed workspace and apply the caller's paging and ordering.
- **FR-007**: Policy replacement MUST use owner-scoped update authorization and reject the unsupported workspace-group subject kind.
- **FR-008**: Audience discovery MUST use the [resource publication predicate](../resource-publication.md#commands-and-queries) before paging.

## Invariants

- **INV-001**: Every event belongs to an existing workspace (checked by the required `workspace_unique_id` column and schema FK `fk_rg_workspace_event_workspace`).
- **INV-002**: Every event references an existing location (checked by schema FK `fk_rg_workspace_event_location`).
- **INV-003**: Persisted event-type ordinals remain `PADEL=0` and `TENNIS=1` (checked by `WorkspacePersistedEnumTest`).

## Consistency

Writes are transactional and use JPA optimistic concurrency. Workspace removal removes
registrations before events and events before their referenced locations. Event publication
rules and publication state are removed and membership-request event origins are cleared
before an event is deleted.

Audience projections contain the event identifier, title, schedule, type, capacity,
publication state, membership state, and an audience location containing name,
description, coordinates, and Google Place ID. They contain no location identifier,
roster, contact descriptor, audit metadata, or persistence version.
`ResourcePublicationService.browseWorkspaceEvents` supplies these projections.
`isResourceVisibleForCurrentUser` checks audience eligibility without an owner bypass.

Maximum participant count is descriptive capacity; [registrations](registrations.md)
can exceed it.

Validation failures carry stable `workspace.event.error.*` keys, including required,
length, schedule, capacity, and location-scope failures. Missing records and stale
writes surface as entity-not-found and optimistic-lock failures where reached.

Instants are persisted independently of locale. Explicit UTC editing/formatting is
the UI contract, not an implicit timezone inferred by the business service.

## Success criteria

- **SC-001**: A valid event round-trips with its workspace, schedule, type, and location (`WorkspaceEventServiceFuncTest`); another workspace's location is refused before persistence (`WorkspaceEventServiceImplTest`).
- **SC-002**: A stale event update writes no event changes (`WorkspaceEventServiceImplTest`).
- **SC-003**: Audience paging includes eligible published events and excludes drafts and denied events before calculating the page total (`WorkspaceMembershipRequestPersistenceFuncTest`).

## Related contracts

- [Resource publication](../resource-publication.md) and [workspace authorization](README.md#authorization-scope).
- [Locations](locations.md), [registrations](registrations.md), and [event UI](../../ui/events.md).

## Evidence

- [WorkspaceEventServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/event/WorkspaceEventServiceImpl.java)
- [WorkspaceEventEntity.java](../../../../rg-logic/src/main/java/vg/rg/entity/workspace/WorkspaceEventEntity.java)
- [005-workspace-event.yaml](../../../../rg-logic/src/main/resources/db/liquibase/005-workspace-event.yaml)
- [WorkspaceEventServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventServiceImplTest.java)
- [WorkspaceEventServiceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/WorkspaceEventServiceFuncTest.java)
- [WorkspaceEventSchemaTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceEventSchemaTest.java)
- [ResourcePublicationServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/resource/ResourcePublicationServiceImpl.java)
- [ResourcePublicationServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/resource/ResourcePublicationServiceImplTest.java)
- [ResourceVisibilityPolicyTest.java](../../../../rg-logic/src/test/java/vg/rg/model/resource/ResourceVisibilityPolicyTest.java)
- [WorkspacePersistedEnumTest.java](../../../../rg-logic/src/test/java/vg/rg/model/workspace/WorkspacePersistedEnumTest.java)
- [WorkspaceEventAudienceModel.java](../../../../rg-logic/src/main/java/vg/rg/model/workspace/WorkspaceEventAudienceModel.java)
- [WorkspaceMembershipRequestPersistenceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceMembershipRequestPersistenceFuncTest.java)
