# Contract: Workspace Lifecycle and Selection

**Module**: `rg-logic` | **Spec**: [../spec.md](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

Business operations over workspaces themselves. All business rules live here; the UI calls these interfaces
and holds none of the rules (Principle VI, FR-027).

## `vg.rg.service.WorkspaceService`

```java
public interface WorkspaceService {

    WorkspaceModel create(WorkspaceModel model);

    WorkspaceModel update(WorkspaceModel model);

    void delete(UniqueId workspaceId);

    List<WorkspaceModel> listOwned();

    Optional<WorkspaceModel> find(UniqueId workspaceId);

    /** The caller's default workspace, provisioning it on first call. */
    WorkspaceModel ensureDefault();
}
```

### Authorization

Every method requires the app-wide `workspace:owner` (FR-028). Methods addressing an existing workspace
additionally require **ownership of that workspace**, checked through the single resource-scoped overload
with a **local workspace capability** (FR-042, FR-045):

| Method | Guard |
|---|---|
| `create`, `listOwned`, `ensureDefault` | `hasAuthority(Permissions.Workspace.OWNER)` — flat check; no resource exists yet |
| `find` | `hasAuthority(#workspaceId, LocalPermissions.Workspace.READ)` |
| `update` | `hasAuthority(#workspaceId, LocalPermissions.Workspace.UPDATE)` |
| `delete` | `hasAuthority(#workspaceId, LocalPermissions.Workspace.DELETE)` |

Here the workspace **is** the resource, so its own identifier is what gets passed; resolution recognizes it
at step 1 and checks ownership immediately.

`LocalPermissions.Workspace.CREATE` is declared for completeness but has no call site: creating a
workspace has no containing resource to address, so it is guarded by the flat app-wide check. It is
deliberately **not** treated as container-addressed either — a workspace is a chain root, so a container
lookup would have nothing to find. It therefore gets no special case anywhere; it exists so that a future
"create inside a container" case has a permission to name.

`listOwned` returns only workspaces owned by the caller and never another user's (FR-003), so it filters by
owner rather than relying on a per-row check.

### Behaviour

The declared local permissions above are **formal**: they are validated as recognized local permissions,
but the caller is not required to hold them, because ownership of the workspace already grants complete
authority (FR-014, FR-015). See [authority-checker.md](./authority-checker.md) for the exact conditions.
They are passed now so that granular non-owner access can be introduced later without editing a single
guard.

**`create`** — validates `name` (**required** here, since this is the user-supplied path; bounded) and
`description` (bounded); refuses when the caller already owns `rg.workspace.max-per-user` workspaces
(FR-009); allocates an identifier from
`UniqueIdService`; writes the workspace in one transaction. `defaultForOwner` is `NULL` — `create` never produces a default. Returns the stored
model. No containment-index row is written: a workspace is a chain root and resolution recognizes it by its
presence in `rg_workspace` (research R1).

**`update`** — updates `name`/`description` only, and a non-blank `name` is required, so this is also how a
system-named workspace gets a real name (FR-046). Rejects a stale `version` with
`ObjectOptimisticLockingFailureException`, which the UI renders as the localized "reload and retry"
guidance (FR-006). `ownerUniqueId`, `defaultForOwner`, `author`, and `createdAt` are immutable.

**`delete`** — refuses when `defaultForOwner` is set (FR-008). Otherwise, in one transaction: removes all
contained objects through the registered `WorkspaceContentContributor`s, then removes the workspace
(research R7). Repoints any `WorkspaceSelection` that referenced it to the caller's default workspace
(FR-008). The confirmation step is the UI's responsibility (FR-007); this method
assumes confirmation already happened.

**`ensureDefault`** — returns the caller's workspace whose `defaultForOwner` is set, creating it if absent
(FR-002) **with `name = null`**, so its label is localized rather than frozen in one language (FR-046). Idempotent and safe under concurrency: the unique index on `defaultForOwner` makes a losing
racer's insert fail, and the implementation recovers by re-reading the existing default rather than
surfacing the constraint violation (research R6). Never provisions for a caller without `workspace:owner`.

### Model

```java
public class WorkspaceModel {
    UniqueId uniqueId;      // null on create
    int version;            // optimistic concurrency
    String name;            // NULL = system-named -> UI renders a localized label (FR-046)
    String description;     // optional, bounded
    boolean defaultWorkspace; // read-only projection of defaultForOwner
}
```

`ownerUniqueId`, `author`, and `lastEditor` are **not** exposed on the model — they are audit and scope
data, never presentation input (FR-022).

**Naming (FR-046)**: `name` is `null` for a workspace the *system* created — the auto-provisioned default
and every workspace the migration creates. `rg-logic` returns `null` plus a stable message **key** for the
label; the UI resolves the key so the label follows the viewer's locale. A user-supplied name is stored
verbatim and displayed as-is in every locale; the transition from system-named to user-named is one-way.
`rg-logic` never stores a name in a single language, which is what keeps Principle V intact for data the
system authored.

## `vg.rg.service.WorkspaceSelectionService`

```java
public interface WorkspaceSelectionService {

    /** The caller's active workspace, provisioning the default if nothing valid is selected. */
    WorkspaceModel activeWorkspace();

    void select(UniqueId workspaceId);
}
```

### Authorization

`activeWorkspace` requires `workspace:owner` via the flat check. `select` requires
`hasAuthority(#workspaceId, LocalPermissions.Workspace.READ)`, so a user can never select a workspace they
do not own.

### Behaviour

**`activeWorkspace`** — reads the caller's selection. When it is absent, or points at a workspace that no
longer exists or is no longer owned by the caller, it falls back to `ensureDefault()` and repairs the
stored selection. This is the single entry point the workspace section uses, so a stale pointer can never
produce an error screen (FR-004, edge cases).

**`select`** — replaces the caller's single selection row. Persisted server-side per user, so it follows
the user across sessions and devices (FR-004). Concurrent selections from two sessions resolve
last-write-wins on a single row, which is why every workspace screen states the active workspace rather
than assuming it (SC-010).

## `vg.rg.service.WorkspaceContentContributor`

```java
public interface WorkspaceContentContributor {
    String resourceType();
    void deleteAllInWorkspace(UniqueId workspaceId);
}
```

The extension seam required by FR-017 and SC-011. `WorkspaceService.delete` iterates every registered
contributor, so a future contained type joins workspace removal by adding one implementation and changes
no access rule and no workspace code. One implementation ships in this feature: locations (`LocationWorkspaceContentContributor`).

Implementations MUST be transactional-participating (called inside the caller's transaction), MUST delete
only rows scoped to the given workspace, and MUST NOT touch another workspace's rows.

## Errors

| Condition | Signal | UI rendering |
|---|---|---|
| Missing `workspace:owner`, or not the owner | `AccessDeniedException` | Localized denial, no existence disclosure (FR-023) |
| Undeclared permission at a call site | `AccessDeniedException` | Denial even for the owner — a programming error, caught fail-closed (SC-020) |
| Blank or over-long name/description on a user-supplied create/update | Validation failure with a stable message key | Localized field validation (FR-001, FR-025) |
| Workspace limit reached | Domain failure with a stable message key | Localized message stating the limit (FR-009) |
| Stale version | `ObjectOptimisticLockingFailureException` | Localized "reload and retry" (FR-006) |
| Removing the default workspace | Domain failure with a stable message key | Localized refusal (FR-008) |
| Workspace not found | `Optional.empty()` / `EntityNotFoundException` | Localized denial, indistinguishable from "not permitted" (FR-023) |

Message **keys** are returned by `rg-logic`; the translations live in the UI module (Principle V).
