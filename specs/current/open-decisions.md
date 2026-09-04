# Open decisions and pending work

Live items that are neither finished nor abandoned, with enough context to act on without the
conversation that produced them.

A feature directory under `specs/NNN-*/` freezes when its feature ships, so anything still open outgrows
it. This file is where that lands. **Remove an entry when it closes** — a stale "open" item is worse than
no list, because it invites re-litigating something already settled.

Last reviewed: 2026-09-04.

## 1. Verify the production frontend bundle in the deployed environment — *status changed*

Tabs on the locations screen rendered their captions but did not respond to clicks in the production
assembly. The cause was diagnosed: a cached `prod.bundle` predating the `workspaces/locations` route, so
the route key the server sent had no branch in the bundle's `loadOnDemand` switch and the chunk defining
`vaadin-tabs`/`vaadin-tabsheet` was never fetched.

**The local cache is now gone** — `rg-frontend-vaadin/src/main/bundles/` holds only its `README.md` — so
the next production build here generates a fresh bundle and the local half of this is closed.

What remains is unverified rather than known-broken: **whether the deployed environment is still running
the old assembly.** Check the deployed app rather than this working copy. The mechanism and the
verification greps are in
[engineering-notes.md](./engineering-notes.md#production-frontend-bundle).

Worth knowing while checking: a stale bundle is not the only way the frontend can look outdated — a
client-cached `styles.css` produces a similar "the code is right but the screen is wrong" impression, and
is diagnosed differently. See the stylesheet-caching note in the same file.

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

## 6. Let an invited participant actually bind to an identity — *blocked on a consent step*

A workspace owner can register a participant, but there is no way for that person to become a platform
user and have their `user_unique_id` filled in. Two pieces are missing, and the second blocks the first.

**An invite issue/redeem pair in the identity service.** Redemption authenticates the person, links
their Telegram channel to a subject, and returns that subject so this application can bind it. Nothing
about *registration* needs identity — that is deliberate, so registration cannot become a
phone-number-lookup oracle — so this is the only part that does.

**The hard prerequisite:** `IdentitySecureAuthorizationFacade.java:41` hardcodes
`consentToKeepPersonalData = false`. Identity's `authenticateTelegram` is consent-gated create-on-demand,
so with `false` and no existing user it returns a provisional principal with `userUniqueId == null` and
persists nothing — and `AuthorityChecker` fails closed on a null subject. **An invited person can
therefore authenticate and do nothing, and the invite can never bind.** A real consent step has to feed
that argument before any invitation flow works end to end.

Contract tests at that boundary are mandatory when it changes: success, validation failure,
authorization failure, timeout, unavailability, incompatible response — plus that consent-given now
yields a non-null subject.

## 7. Two latent bugs that only real membership surfaces — *dormant, recorded deliberately*

Both are harmless while a workspace has exactly one owner, and both become live the moment a non-owner
can select or use someone else's workspace. Whichever change introduces membership owns them.

- `WorkspaceServiceImpl.repointSelectionAwayFrom` repoints only the **caller's** selection, so a
  non-owner's selection pointing at a removed workspace would violate the foreign key.
- `WorkspaceSelectionServiceImpl.activeWorkspace()` falls back to provisioning a workspace **owned by the
  caller**, which is the wrong answer for someone who is a member of one rather than an owner of any.
