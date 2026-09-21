# Workspace domain

## Responsibility and ubiquitous language

A workspace is a private container owned by one abstract identity. Locations,
participants, and scheduled events are its content. An event registration joins one
event to one participant in that workspace.

| Term | Meaning |
| --- | --- |
| Owner | The only identity with workspace authority, subject to the app-wide gate |
| Default workspace | Owner-marked workspace protected from removal |
| Active workspace | Persisted per-user selection used to scope the UI |
| System-named | Workspace with a null stored name, rendered through a localized label |
| Participant | Locally registered person record, not a member or permission holder |
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
store a workspace column. Owner, author, and last-editor identifiers have distinct
roles; audit fields do not confer access.

Foreign keys are restrictive. Workspace removal repoints the selection and invokes
ordered content contributors: registrations, events, participants, then locations.
No database cascade substitutes for this service behavior.

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
