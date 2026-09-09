# RG Telegram application

## rg-logic

- Defines the application contracts and implements the secure authorization boundary and application services.

## rg-frontend-vaadin

- Provides the mobile-first Vaadin application and Telegram authentication entry point.

### Frontend connector tests

The browser-side time-picker connector is verified separately from the Java Gradle test suite:

```bash
cd rg-frontend-vaadin
node --experimental-strip-types --test src/test/frontend/ts/datetime/temporal-time-picker.test.mjs
```

## Authorization facade selection

- Authorization services are always enabled. Setting `rg.dev-secure-service.enabled=true` explicitly
  selects the permissive `DevSecureAuthorizationFacade`; this setting belongs only in ignored
  local development configuration.
- When `rg.dev-secure-service.enabled` is false or absent, `IdentitySecureAuthorizationFacade`
  delegates Telegram authentication to `IdentityApplicationApi` through `identity-rest-client`.
- Configure the REST client with `VG_IDENTITY_REST_CLIENT_BASE_URL` and the required secret
  `VG_IDENTITY_REST_CLIENT_API_KEY`; no Spring profile is required.
- The adapter sends no personal-data consent because the current RG request has no explicit user
  consent signal. Existing identity users can authenticate; a provisional principal without `sub`
  is denied until a consent flow is implemented.

## Two tables are retained but unmapped

Locations live only inside workspaces, in `rg_workspace_location`. Two tables from before that remain on
disk with **no entity, no repository and no code path reaching them**. An architecture test enforces that
nothing in either module so much as names them.

- **`rg_location`** — the former shared collection. Kept as the rollback for the one-time copy into
  per-author workspaces. Its table comment marks it superseded.
- **`rg_migration_marker`** — one row per completed one-time migration. A
  `GLOBAL_LOCATION_TO_WORKSPACE` row means that copy ran in this environment.

**The one-time migration itself has been removed from the codebase.** It ran once per environment and
its job is done, so keeping a startup path that can never fire again was worse than deleting it. Two
consequences follow, and both are deliberate:

- An environment that never started the application while the migration existed will **never** copy its
  old rows. Restoring that capability means recovering the code from the commit that removed it, not
  flipping a property.
- The verification described in Scenario A0 of
  [specs/003-workspace-layer/quickstart.md](specs/003-workspace-layer/quickstart.md) can no longer be
  re-run. It does not need to be: completion was **confirmed manually on 2026-09-03**, before the code was
  removed. The marker row plus that record are the remaining evidence — which is why
  `rg_migration_marker` was kept rather than dropped alongside the code.

**Dropping `rg_location` is therefore unblocked** as a separate change: its gate was confirming the copy
ran, and that is done. Keep `rg_migration_marker` when you do — the row records why the drop was safe.

A future one-time migration may write to `rg_migration_marker` again by re-adding its own mapping; the
table's shape (`name`, `completed_at`) is migration-agnostic.
