# Quickstart: Validating the Workspace Layer

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

How to run and verify this feature end to end. Contracts and schema details are linked rather than
repeated; implementation belongs in `tasks.md`.

## Prerequisites

- JDK 21 (the build sets `sourceCompatibility = '21'`).
- A running Docker daemon — integration tests start MySQL 8 through
  `vg.test.containers.starters.Mysql8ContainerStarter`. On macOS with Colima, confirm the daemon is
  reachable before running them.
- A local datasource for manual runs, configured in the git-ignored
  `rg-frontend-vaadin/src/main/resources/application-local.properties` (see
  `application-local.example.properties`).
- To exercise the UI as a permission holder, the authorization facade must grant `workspace:owner`. In
  local development that is `DevSecureAuthorizationFacade`, selected with
  `rg.dev-secure-service.enabled=true` in local-only configuration.

## Automated verification

Narrowest task first, then broaden — the project convention.

**1. Business logic unit tests** (fast, no Docker):

```bash
./gradlew :rg-logic:test --tests 'vg.rg.security.*' --tests 'vg.rg.service.Workspace*'
```

Expect: the four enforced authority conditions each denying in isolation and allowing together; an owner
holding **none** of the local capabilities allowed (SC-006); an undeclared or app-wide permission denied
even for an owner (SC-020); workspace validation, bounds, and lifecycle rules; `WorkspaceLocationService`
scope handling.

**2. The security boundary at depth** — the core of this feature:

```bash
./gradlew :rg-logic:test --tests '*WorkspaceScopeResolver*' --tests '*AuthorityChecker*'
```

Expect, per [contracts/authority-checker.md](./contracts/authority-checker.md): with a test-only
`WorkspaceScopeProvider` resolving a chain at least 5 levels deep
(workspace → group → event → comments → comment), the workspace owner is allowed when passing **any**
identifier in the chain, a non-owner is denied for every one, and each check issues exactly **one** query
(SC-015); a workspace identifier with `workspace:update` resolves to itself; a real location identifier
with `location:update` resolves through the join; a workspace identifier with `location:create` resolves
via the container lookup while the same identifier with `location:update` **denies** (FR-048, SC-023); and
an unknown identifier, a permission no provider claims, and a row that does not join to a workspace all
deny **and emit a warning** (SC-016).

**3. Scope isolation against a real database**:

```bash
./gradlew :rg-logic:test --tests '*WorkspaceLocation*FuncTest'
```

Expect: a workspace read returns zero rows from any other workspace (SC-001); removing a workspace deletes
its locations and leaves other workspaces' counts unchanged (SC-004). Note the scope is structural now —
`workspace_unique_id` is `NOT NULL` — so this test is regression cover rather than the primary defence.

**3b. The one-time migration**:

```bash
./gradlew :rg-logic:test --tests '*GlobalLocationMigration*'
```

Expect, per [contracts/workspace-location-service.md](./contracts/workspace-location-service.md): seeding
`rg_location` with L rows across A distinct authors yields exactly A default workspaces each holding
exactly L locations — A × L in total — with name, description, coordinates, Google Place ID, and original
author preserved (SC-017); a second run adds 0 workspaces and 0 copies (SC-018); an injected failure
mid-run rolls **everything** back — 0 workspaces, 0 copies, no marker (FR-047, SC-022); the source rows in
`rg_location` are unmodified after both a failed and a successful run (FR-041); and
`rg.workspace.migration.enabled=false` performs nothing at all.

**4. UI navigation and selector placement**:

```bash
./gradlew :rg-frontend-vaadin:test --tests '*MainView*' --tests '*Workspace*'
```

Expect: the nav item present with `workspace:owner` and absent without it (SC-012); the selector on every
route inside the section and on none outside it (SC-010); a switch in one action replacing the child view's
content (SC-003).

**5. Full suite**, since the change touches both modules:

```bash
./gradlew test
```

## Manual verification

Start the application:

```bash
./gradlew :rg-frontend-vaadin:bootRun
```

Serve over **https** for any Telegram Mini App or geolocation check — the existing constraint from
[../current/geolocation.md](../current/geolocation.md) still applies.

### Scenario A0 — the migration ran (FR-037 – FR-041)

Do this first on any environment with existing location data.

1. Before deploying, note `select count(*) from rg_location` (L) and
   `select count(distinct author) from rg_location` (A).
2. Deploy and start the application.
3. **Expect**: `rg_workspace` holds A rows all carrying `default_for_owner` and all with `name IS NULL`
   (system-named, localized labels — FR-046);
   `select count(*) from rg_workspace_location` equals **A × L**; `rg_migration_marker` holds the
   `GLOBAL_LOCATION_TO_WORKSPACE` row; and `rg_location` is **untouched** (SC-017).
4. Restart. **Expect**: no additional workspaces and no additional copies (SC-018).
5. Sign in as one of the migrated authors. **Expect**: their default workspace is active and contains all
   L locations, with content and attribution intact.
6. **Expect** `rg_location` to still exist, unmodified, with a table comment marking it unused and naming
   `rg_workspace_location` as its replacement. Nothing in this feature drops it. Record the verified A × L
   figure — dropping the table is a separate later change gated on this check (FR-037).

### Scenario A — first entry provisions a workspace (User Story 1)

1. Sign in as a user holding `workspace:owner` who has never used the application.
2. Confirm the workspace item appears in the drawer.
3. Open it. **Expect**: a default workspace already exists and is active, and the selector shows its
   **localized** label (its stored name is empty) — no setup step was required (FR-002, FR-046, SC-002).
   Switch locale and confirm the label follows; then name the workspace and confirm the stored text now
   shows in both locales (SC-021).
4. Open the workspace locations screen and add a location.
5. **Expect**: it is listed here, and `/locations` no longer resolves — there is no global screen to check
   it against (FR-019, SC-019).

### Scenario B — isolation and switching (User Story 2)

1. Create a second workspace and add a different location to it.
2. Switch the selector between the two. **Expect**: each switch replaces the visible content entirely, with
   nothing carried over, in one action (SC-003).
3. Search inside one workspace for a name that exists only in the other. **Expect**: no results.
4. Sign out and back in. **Expect**: the same workspace is active (FR-004).

### Scenario C — the permission gate (User Story 3)

1. Sign in as a user **without** `workspace:owner`.
2. **Expect**: no workspace nav item, and nothing indicating the section exists (FR-030).
3. Navigate directly to `/workspaces`. **Expect**: denied, with no disclosure of existence, and **no**
   workspace provisioned for this user (FR-031, SC-012).
4. Confirm the remaining app-wide areas this user has permission for behave exactly as before (SC-014).
   Note that such a user now reaches **no** locations at all, since locations live only inside workspaces.
5. Revoke `workspace:owner` from a user who already had workspaces with content. **Expect**: the nav item
   and selector disappear and every workspace action is denied, while nothing is deleted; re-granting
   restores access to the same content (FR-033, SC-013).

### Scenario D — depth-independent authority (FR-034)

No nested types ship in this feature, so the depth behaviour is verified by test 2 above rather than
through the UI. The checks to make manually are architectural:

1. Every workspace-scoped service method guards on
   `@authorityChecker.hasAuthority(<resourceId>, <localPermission>)` — a **single** check, with no second
   workspace-only variant anywhere (FR-045).
2. For an operation on an existing object, the identifier passed is **that object's** — e.g. `update`
   passes the workspace-location's id, not its workspace. Only `create` passes a container.
3. No call site resolves a workspace by hand.
4. Each guard's permission **matches the identifier it passes**: a resource-addressed guard uses a
   non-create verb, and a container-addressed guard (`create`) passes the container. A mismatch denies at
   runtime rather than failing to compile (FR-048, SC-023), so this is worth reading for directly.

A type added later then inherits depth-independence by registering one `WorkspaceScopeProvider` whose
`findByResource` joins its full path in a single query, with no access-rule change (SC-011).

### Scenario E — lifecycle and safety (User Story 4)

1. Rename a workspace. **Expect**: the new name appears wherever the active workspace is named.
2. Remove a non-default workspace holding locations. **Expect**: a confirmation stating the contents will
   be removed; on confirm, its locations are gone, every other workspace's contents are unchanged, and
   another workspace becomes active (FR-007, FR-008, SC-004).
3. Attempt to remove the default workspace. **Expect**: a localized refusal (FR-008).
4. Open the same workspace in two sessions, save both. **Expect**: the second save is rejected with the
   localized "reload and retry" guidance (FR-006).
5. Create workspaces up to `rg.workspace.max-per-user`, then one more. **Expect**: a localized refusal
   stating the limit; existing workspaces stay usable (FR-009).

### Scenario F — presentation (Principle V)

1. At the narrowest supported width, walk every workspace flow. **Expect**: no horizontal scrolling; a
   maximum-length workspace name stays readable and does not break the layout (SC-009).
2. Switch to the non-default locale. **Expect**: every string translated — nav item, titles, selector,
   the system-named workspace's label, confirmations, refusals, validation, denials — with no missing-key
   fallback visible (FR-025, FR-046).

## Configuration reference

| Property | Default | Purpose |
|---|---|---|
| `rg.workspace.max-per-user` | 20 | Workspace count bound (FR-009) |
| `rg.workspace.name-max-length` | 128 | Name bound (FR-001) |
| `rg.workspace.description-max-length` | 1024 | Description bound (FR-001) |
| `rg.workspace.migration.enabled` | true | Lets an environment skip the one-time migration (FR-037) |

## What "done" looks like

Every scenario above passes, `./gradlew test` is green, and — per the constitution's actualization rule —
`specs/current/` carries a workspace domain file describing the implemented behaviour, while
[../current/geolocation.md](../current/geolocation.md) has been **rewritten**: its "shared collection"
framing is superseded, since a location is now workspace-scoped and the proximity suggestion is scoped to
the active workspace. That actualization is the final phase of `tasks.md`, not of this plan.

One item to carry into the release note: `rg_location` remains on disk, unread and marked unused — that is
the durable rollback, not an oversight (research R10). Dropping it is a separate later change, to be made
only after Scenario A0 has been verified against real data. Until then, recovering from a bad migration is
a redeploy plus a re-run rather than a database restore.
