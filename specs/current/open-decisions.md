# Open decisions and pending work

Live items that are neither finished nor abandoned, with enough context to act on without the
conversation that produced them.

A feature directory under `specs/NNN-*/` freezes when its feature ships, so anything still open outgrows
it. This file is where that lands. **Remove an entry when it closes** — a stale "open" item is worse than
no list, because it invites re-litigating something already settled.

Last reviewed: 2026-09-03.

## 1. Rebuild and verify the production frontend bundle — *blocking a known prod defect*

Tabs on the locations screen render their captions but do not respond to clicks in the production
assembly. Cause is diagnosed: the cached `prod.bundle` predates the `workspaces/locations` route, so the
route key the server sends has no branch in the bundle's `loadOnDemand` switch and the chunk defining
`vaadin-tabs`/`vaadin-tabsheet` is never fetched.

Needs a machine with node/npm — this cannot be done from an environment without it, which will silently
reuse the cache while reporting success. Full mechanism and the verification greps are in
[engineering-notes.md](./engineering-notes.md#production-frontend-bundle).

## 2. Drop `rg_location` — *unblocked, not urgent*

The gate was confirming that the one-time copy into per-author workspaces actually ran. That was
**verified manually on 2026-09-03**, before the migration code was removed, and cannot be re-verified now.

So this is available whenever wanted: one Liquibase `dropTable`, plus extending
`PermissionDeclarationArchitectureTest` from "no production code names `rg_location`" to also asserting
the table is gone.

**Keep `rg_migration_marker` when you do.** Its `GLOBAL_LOCATION_TO_WORKSPACE` row, together with the
dated note above, is the record of *why* the drop was safe. Dropping the subject and its evidence together
would leave nothing explaining the decision.

## 3. Walk quickstart scenarios A–F — *the remainder of T086*

Scenario A0 is closed (see item 2). A–F are the UI walkthroughs in
[../003-workspace-layer/quickstart.md](../003-workspace-layer/quickstart.md) and need a Telegram Mini App
session over https, so they cannot be discharged locally. Their automated equivalents all pass.

## 4. Restore the workspace section's navigation entry — *deliberately withheld*

The section has no drawer entry at present; its routes, layout and permission gate all work. Restoring it
is one `addNav` call in `MainView` plus a `nav.workspaces` label in both bundles. `WorkspaceNavigationTest`
currently asserts its **absence**, so that assertion inverts when the entry returns.

## 5. Consider a freshness test for `specs/current/` — *not started*

These documents are self-reported: nothing fails when they drift. The actualization commit for feature 003
shipped two references to classes it deleted in the same commit, which is the failure mode. A test in the
style of the existing architecture tests — every class named in `specs/current/` exists on disk, every
`@Route` value appears somewhere in these documents — would catch that on every commit, including changes
that never get a task plan (which is where most drift originates).

One pitfall found while prototyping it: match only *this project's* types. A naive sweep of backticked
CamelCase names flags framework classes these documents legitimately mention — `ApplicationRunner`,
`@SpringBootTest` — as missing. Resolve candidates against the `vg.rg` sources rather than against every
`.java` stem, or restrict the pattern to names the repo actually declares.
