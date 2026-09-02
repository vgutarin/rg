# Workspace

Current-state specification of the workspace layer as implemented. Full requirements, clarifications, and
rationale live in [../003-workspace-layer/spec.md](../003-workspace-layer/spec.md); design in
[../003-workspace-layer/plan.md](../003-workspace-layer/plan.md).

See also [geolocation.md](./geolocation.md) — a location is a workspace's content, scoped by the active
workspace, though its screen is reached from a top-level navigation entry.

## Purpose

A **workspace** is a private container a single user works inside. Everything a workspace holds derives
its access from the workspace: **owning it grants complete authority over its contents, at any depth.**

Locations are the only contained type today. The layer exists so that further types (contacts, groups)
inherit that authority by construction rather than by each one re-deriving access rules.

## The container

- **Single-owner.** A workspace cannot be shared; there is no membership, invitation, or role. `owner`
  is the scope authority and the only identity field used for access. `author`/`lastEditor` remain
  audit-only, as everywhere else in the application.
- **One active at a time.** The active workspace is stored per user and survives sign-out, so returning
  lands in the same place.
- **A default that always exists.** A permission holder's first entry provisions one, so no setup step is
  required, and it can never be removed — that is what guarantees the user always has somewhere to work.
  If a removal would leave the owner with none, another default is provisioned.
- **System-named vs user-named.** A workspace the *system* created stores **no name**: a stored name
  cannot follow the viewer's locale, so `name IS NULL` means "render the localized label". Naming it is a
  one-way transition — from then on the stored text shows verbatim in every locale.
- **Bounded.** `rg.workspace.max-per-user` (default 20) workspaces per user; name and description lengths
  bounded by configuration. Exceeding the count is a localized refusal that leaves existing workspaces
  usable.

## Capabilities

- **List / create** the workspaces you own, and **rename** one — which is also how a system-named
  workspace acquires a stored name.
- **Remove** a non-default workspace. Confirmed first, and the confirmation states plainly that the
  contained objects go too. The default workspace is **not offered** for removal rather than offered and
  refused — an affordance that leads only to a refusal is a dead end.
- **Switch** the active workspace from the selector. One action replaces the visible content entirely;
  nothing carries over.
- **Work inside** the active workspace: the locations screen at `/workspaces/locations` behaves exactly as
  the retired global screen did, restricted to one workspace.

Concurrent edits use optimistic concurrency (JPA `@Version`). A stale save is rejected with the localized
"reload and retry" guidance, and the dialog keeps the typed text so the user retries rather than retypes.

## Access control

Two permission declarations, deliberately disjoint.

- **`Permissions`** — app-wide capabilities, meaningful without naming a resource. The layer's gate is
  **`workspace:owner`**: holding it means the user may own and use workspaces at all. It is the **only**
  gate on the whole section — a second gate on a contained capability would hide a screen the owner can
  actually use.
- **`LocalPermissions`** — capabilities that only mean something *inside* a workspace
  (`location:read|list|create|update|delete`, `workspace:create|read|update|delete`). These are never
  tested against what a principal *holds*, so they never become Spring authorities and are dropped from
  the sanitised permission set. The flat, resource-less check **rejects every one of them**.

### One check, resource-addressed

Every workspace-scoped service method guards with a single expression:

```java
@PreAuthorize("@authorityChecker.hasAuthority(#resourceId, '<localPermission>')")
```

The caller passes the identifier of **the thing being acted on** — a location's own id for
`update`/`delete`, the workspace for `create` and for reads. Four conditions must hold: an authenticated
principal, a known user identity, `workspace:owner`, and ownership of the resolved workspace.

**Ownership overrides the declared capability.** The local permission is validated as *declared* but is
not required to be *held* — an owner holding none of them is still allowed, because owning the workspace
already grants complete authority over its contents. The permission's role is to say which *type* is
being addressed, not to be checked against the principal.

### Resolving a resource to its workspace

The declared permission's resource part selects a `WorkspaceScopeProvider`; its verb selects the lookup —
`create`/`list` address the **container** (the identifier names the workspace, because no resource id
exists yet), every other verb addresses the **resource**.

That is **one query per check, flat in how deep the type sits**: a provider's `findByResource` joins its
full path to the workspace in a single query. There is no containment index, no traversal loop, and
therefore no depth bound to configure. A type added later inherits depth-independence by registering one
provider and changing no access rule.

Resolution **fails closed** at every step — an unrecognized permission, a permission no provider claims,
two providers claiming one permission, a missing row, or a row that does not join to a workspace all deny.
Each emits a warning naming the resource and permission only, so an otherwise unexplained denial is
diagnosable without user data reaching the log.

A guard whose permission does not match the identifier it passes (a container-addressed permission with a
resource id, or the reverse) **denies at runtime** rather than failing to compile.

## Presentation

**The workspace section has no navigation entry at present.** Its routes, layout and gate all work; only
the way in from the drawer is withheld, pending further work on the section itself. Restoring it is one
`addNav` call plus its label.

**Locations sit at the top level**, because the workspace is the *scope* a location lives in rather than a
place the user must navigate through. The entry appears only when
`hasAuthority(activeWorkspaceId, "location:list")` holds — a question about the workspace being worked in,
not about a capability the principal carries around. The app-wide gate is checked first, and not as a
shortcut: resolving the active workspace is itself guarded by it, so asking a non-holder would raise an
access denial and take the navigation shell down with it. The entry still opens the workspace-scoped route
`/workspaces/locations`; the retired global route stays gone.

One consequence worth naming: resolving the active workspace is what provisions a default, so a permission
holder now gets theirs on their first page load rather than on their first visit to a workspace screen.
Same guarantee, reached earlier. A user without the permission still gets nothing.

The active-workspace selector is hosted by a nested router layout, so it appears on every route inside the
section and **structurally cannot** appear outside it. The active workspace is named beneath it **only
when it is not the default** — the default is the implicit place to be, so naming it says nothing the user
had not already assumed.

Mobile-first throughout: one column, full-width actions, long names wrapped rather than clipped, and wider
layouts reached only through `min-width` queries. All text is internationalized, Ukrainian default with
English second; a system-named workspace's label follows the viewer's locale.

## Data

- **`rg_workspace`** — nullable `name` (null = system-named) and `description`, `owner_unique_id`,
  `default_for_owner`, version token, audit columns. `default_for_owner` carries the owner's id on their
  default workspace and null on every other; the **unique index** on it permits at most one default per
  owner while allowing any number of non-defaults, because MySQL unique indexes ignore nulls. That closes
  the concurrent-provisioning race in the database rather than in application code.
- **`rg_workspace_location`** — as `rg_location` was, plus `workspace_unique_id` **NOT NULL and never
  updated**. A location without a workspace is unrepresentable and a location cannot move between
  workspaces. The column serves two purposes: it scopes every query by construction, and it is the link
  the authority check joins through.
- **`rg_workspace_selection`** — the active workspace per user, keyed by user, with a foreign key to
  `rg_workspace`. That key is why a removal must repoint the selection **before** deleting the workspace
  row.
- **`rg_migration_marker`** — one row per completed one-time migration. Unmapped; see below.

No personal data about natural persons is persisted. Free-text fields carry localized guidance
discouraging others' personal data and are stored as given.

## The retired global scope

`rg_location` held a single collection shared by every permission holder. It no longer has a service, a
repository, a mapper, an entity, a view, or a route, and `Permissions.Location.*` is gone.

A one-time migration copied that collection into one system-named default workspace per author — **every
author received every location**, because the collection was shared and a subset would silently have taken
places away from someone, so the result was A × L rows. It ran at startup as a single all-or-nothing
transaction and wrote its marker last, so the marker's presence means the whole copy committed.

**That migration has since been deleted**, after its completion was confirmed manually in the deployed
environment on 2026-09-03; the code survives in the history of this branch. It could only ever run once per environment,
and a startup path that can never fire again is worse than no path at all. An environment that never
started the application while it existed will not copy its old rows.

Two tables remain on disk, both **entirely unmapped** — no entity, no repository, nothing that reaches
them, enforced by an architecture test:

- **`rg_location`**, the rollback for that copy, with a table comment marking it superseded. Dropping it
  is a **separate later change**, now unblocked: its gate was confirming the copy ran, which is done.
- **`rg_migration_marker`**, one row per completed one-time migration. Its
  `GLOBAL_LOCATION_TO_WORKSPACE` row is now the only remaining evidence that the copy ran, which is why
  it was kept when the code went. Its shape (`name`, `completed_at`) is migration-agnostic, so a future
  one-time migration can write here again by re-adding its own mapping.

## Where it lives

- **`rg-logic`** (business rules):
  - Access: `AuthorityChecker` (both overloads), `WorkspaceScopeResolver`/`Impl`,
    `WorkspaceScopeProvider`, `WorkspaceScope`, `WorkspaceSelfScopeProvider`, `LocationScopeProvider`,
    `Permissions`, `LocalPermissions`, `PermissionSyntax`.
  - Services: `WorkspaceService`/`Impl`, `WorkspaceLocationService`/`Impl`,
    `WorkspaceSelectionService`/`Impl`, `WorkspaceContentContributor` (the removal seam) with
    `LocationWorkspaceContentContributor`, `WorkspaceLimitReachedException`,
    `WorkspaceNotRemovableException`.
  - Data: `WorkspaceEntity`, `WorkspaceLocationEntity`, `WorkspaceSelectionEntity`, their repositories,
    `WorkspaceScopeRow`, `UniqueIdRow`, `WorkspaceProperties`.
  - Schema: `rg-logic/src/main/resources/db/liquibase/002-workspace-init.yaml`.
- **`rg-frontend-vaadin`** (UI): `WorkspaceLayout` (the nested layout hosting the selector),
  `WorkspacesView` (`/workspaces`), `WorkspaceLocationsView` (`/workspaces/locations`),
  `LocationFormDialog`, `MapsResolutionBridge` (workspace-scoped proximity), the workspace navigation
  section in `MainView`, and the `workspace-*` rules in
  `rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css`.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `rg.workspace.max-per-user` | 20 | Workspace count bound per user |
| `rg.workspace.name-max-length` | 128 | Name bound |
| `rg.workspace.description-max-length` | 1024 | Description bound |
