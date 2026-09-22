# Workspace domain

## Responsibility and ubiquitous language

A workspace is a container owned by one abstract identity. Locations, participants,
and scheduled events are its content. A bound participant is a member for event
audience-policy matching only; ownership remains the management authority. An event
registration joins one event to one participant in that workspace.

| Term | Meaning |
| --- | --- |
| Owner | The only identity with workspace authority, subject to the app-wide gate |
| Default workspace | Owner-marked workspace protected from removal |
| Active workspace | Persisted per-user selection used to scope the UI |
| System-named | Workspace with a null stored name, rendered through a localized label |
| Participant | Local person record; a non-null identity binding makes it a member for event visibility only |
| Event | Scheduled Padel/Tennis record, not a DDD domain event |
| Registration | Ordered association between an event and a workspace participant |

Workspace ownership does not make all contained records a single aggregate.
Commands have their own service transaction boundaries. Workspace removal coordinates
their deletion within the workspace-service transaction.

## Authorization scope

[Identity and access](../identity-access.md) owns authentication and the app-wide gate.
For scoped operations, `AuthorityChecker` resolves the resource through
`WorkspaceScopeResolver` and compares the resulting owner to the caller's subject.

The declared local permission selects exactly one `WorkspaceScopeProvider`.
Create/list on a contained type address the workspace; other operations address the
resource. Workspace creation uses the app-wide gate, not the declared local
`workspace:create` value. Event roster management addresses the event; registration
delete/reorder address the registration.

A provider resolves the full path to the workspace in one query, including
registration → event → workspace. The resolver has no recursive traversal or depth
configuration. Unknown declarations, no matching provider, multiple matching providers,
missing resources, and missing workspace joins deny access. Diagnostics contain
resource identifiers and permission names, not contact data. A mismatched identifier
and permission denies at runtime.

## Model relationships and persistence

Direct children have required, immutable `workspace_unique_id` values; they cannot
move between workspaces. Registrations derive scope through their event and do not
store a workspace column. Membership requests are direct children; their optional
event origin is cleared before standalone event removal. Owner, author, and last-editor
identifiers have distinct roles; audit fields do not confer access.

Foreign keys are restrictive. Workspace removal repoints the selection and invokes
ordered content contributors: registrations and membership requests, then events, then direct
workspace children (participants and locations). A contributor with multiple parent relationships
runs immediately before its lowest-ordered parent: membership requests before events because of the
optional event-origin FK, and registrations before their event and participant. Participants and
locations share an order because they do not reference one another.
No database cascade substitutes for this service behavior.

## Functional requirements

- **FR-001**: Scoped management MUST require the app-wide workspace-owner permission and ownership of the resolved workspace.
- **FR-002**: Scope resolution MUST select exactly one provider from the declared local permission and use its container or resource lookup as declared.
- **FR-003**: Unknown permissions, ambiguous providers, absent resources, and failed workspace joins MUST deny access.
- **FR-004**: Registration scope MUST resolve through its event to the workspace without a separate registration workspace column.
- **FR-005**: Workspace removal MUST invoke content contributors in dependency order before deleting the workspace.

## Invariants

- **INV-001**: Holding every local permission without the app-wide owner gate grants no workspace management access (checked by `WorkspaceOwnershipAuthorityFuncTest`).
- **INV-002**: A resource identifier paired with another resource type's permission cannot gain access through fallback probing (checked by `WorkspaceScopeResolverFuncTest`).
- **INV-003**: Contributor order places registrations before events and participants, and membership requests before events (checked by `WorkspaceContentContributorOrderTest` and `EventWorkspaceContentContributorOrderTest`).

## Success criteria

- **SC-001**: A workspace owner can manage contained resources without holding their local permission strings, while another user receives no scoped authority (`WorkspaceOwnershipAuthorityFuncTest`).
- **SC-002**: Removing a workspace deletes its dependent content without violating restrictive foreign keys (`WorkspaceDeletionFuncTest`).

## Capability contracts

- [Lifecycle and selection](lifecycle.md).
- [Locations](locations.md).
- [Participants and contact data](participants.md).
- [Events](events.md).
- [Event registrations](registrations.md).
- [Workspace UI](../../ui/workspaces.md).

## Evidence

- [WorkspaceScopeResolverImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceScopeResolverImpl.java)
- [LocalPermissions.java](../../../../rg-logic/src/main/java/vg/rg/model/security/LocalPermissions.java)
- [WorkspaceScopeResolverTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceScopeResolverTest.java)
- [WorkspaceScopeResolverFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceScopeResolverFuncTest.java)
- [WorkspaceOwnershipAuthorityFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceOwnershipAuthorityFuncTest.java)
- [WorkspaceScopeResolutionObservabilityTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceScopeResolutionObservabilityTest.java)
- [WorkspaceContentContributorOrderTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceContentContributorOrderTest.java)
- [EventWorkspaceContentContributorOrderTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/event/EventWorkspaceContentContributorOrderTest.java)
- [WorkspaceDeletionFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceDeletionFuncTest.java)
