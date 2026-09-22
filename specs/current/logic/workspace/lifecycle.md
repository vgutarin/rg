# Workspace lifecycle and selection

## Responsibility and model

A workspace has a generated `unique_id`, required owner, optional name/description,
optional `default_for_owner`, version, and audit fields. A null name denotes a
system-named workspace. `default_for_owner` carries the owner's identifier only on
the default; its unique index allows at most one default per owner.

A selection is one `rg_workspace_selection` row keyed by user, with a foreign key
to the selected workspace. Selection persists across sessions; concurrent selections
write the same row rather than accumulating per-device choices.

## Commands and queries

| Operation | Behavior |
| --- | --- |
| Create | Require owner gate, validate supplied name/description and count, create non-default owned workspace |
| List owned | Query by caller's owner identifier, ordered by creation time |
| Find | Scoped read returning an optional workspace |
| Ensure default | Return existing default or provision a null-name default |
| Update | Scoped update of name/description with the read version |
| Delete | Refuse default; repoint selection, remove content, then remove workspace |
| Active workspace | Return selected workspace only if still owned; otherwise ensure default and repair pointer |
| Select | Scoped read authorization, then store the workspace under the caller's identifier |

User-supplied names are required after trimming; descriptions are trimmed to null
when blank. Updating a system-named workspace requires a nonblank name, so it cannot
return to a null name through editing. Ownership, default status, author, and creation
time are not writable through update.

## Functional requirements

- **FR-001**: User-created workspaces MUST belong to the caller and MUST be non-default.
- **FR-002**: Create/update MUST require a trimmed nonblank name and enforce the configured name and description bounds.
- **FR-003**: Creation MUST refuse a request when the observed owned-workspace count reaches the configured maximum.
- **FR-004**: Updating MUST reject a stale version and change only name and description.
- **FR-005**: Deleting a default workspace MUST be refused; deleting a selected non-default workspace MUST repoint the caller's selection before removal.
- **FR-006**: Active-workspace lookup MUST return a still-owned selection or ensure a default and persist the repaired pointer.

## Invariants

- **INV-001**: An owner has at most one marked default workspace (checked by schema constraint `uq_rg_workspace_default_for_owner`).
- **INV-002**: A user has at most one persisted selection, pointing to an existing workspace (checked by schema primary key `pk_rg_workspace_selection` and FK `fk_rg_workspace_selection_workspace`).
- **INV-003**: Updating cannot transfer ownership or change the default marker (checked by `WorkspaceServiceImplTest`).

## Consistency

Create/update/delete and selection changes are transactional service operations.
Updates compare the supplied version and use JPA versioning; stale updates raise an
optimistic-lock failure. Default provisioning catches a unique-constraint race and
attempts to reread the existing default.

Deletion rejects default workspaces with `WorkspaceNotRemovableException`. If the
caller's selection points to the deleted workspace, it is moved to the default and
flushed before the workspace row is removed. Contributors remove contained records
in the order defined by [workspace scope](README.md). If deletion leaves no owned
workspaces, a default is provisioned.

`WorkspaceLimitReachedException` exposes the configured limit. Required/overlong
name and description failures carry stable localization keys without the rejected text.
The count check is in the service; the schema's unique-default constraint is a separate
guarantee from the configurable maximum count.

## Configuration

| Property | Default |
| --- | --- |
| `rg.workspace.max-per-user` | 20 |
| `rg.workspace.name-max-length` | 128 |
| `rg.workspace.description-max-length` | 1024 |

Positive bounds and blank-value fallback use [configuration holders](../../engineering/configuration.md).
[Workspace UI](../../ui/workspaces.md) owns default-label localization and confirmation.

## Success criteria

- **SC-001**: First active-workspace lookup returns a system-named default and makes it active (`WorkspaceSelectionFuncTest`).
- **SC-002**: Selecting another workspace survives a new session and replaces the user's selection row rather than adding another (`WorkspaceSelectionFuncTest`).
- **SC-003**: Deleting the selected non-default workspace leaves its content removed and the default active (`WorkspaceDeletionFuncTest`).

## Evidence

- [WorkspaceServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceServiceImpl.java)
- [WorkspaceSelectionServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceSelectionServiceImpl.java)
- [WorkspaceProperties.java](../../../../rg-logic/src/main/java/vg/rg/config/WorkspaceProperties.java)
- [002-workspace-init.yaml](../../../../rg-logic/src/main/resources/db/liquibase/002-workspace-init.yaml)
- [WorkspaceServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceServiceImplTest.java)
- [WorkspaceSelectionFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceSelectionFuncTest.java)
- [WorkspaceDeletionFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceDeletionFuncTest.java)
- [WorkspacePermissionRevocationFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspacePermissionRevocationFuncTest.java)
