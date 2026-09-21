# Open decisions and pending work

Live items that are neither finished nor abandoned, with enough context to act on without the
conversation that produced them.

Current contracts are indexed in [UI](ui/README.md) and [logic](logic/README.md).
Documentation maintenance instructions are in [specs/README.md](../README.md#documentation-authoring-and-maintenance).

Documentation/source review: 2026-09-21. Deployment and device checks remain unverified.

## 1. Verify the production frontend bundle in the deployed environment — *status changed*

Tabs on the locations screen rendered their captions but did not respond to clicks in the production
assembly. The cause was diagnosed: a cached `prod.bundle` predating the `workspaces/locations` route, so
the route key the server sent had no branch in the bundle's `loadOnDemand` switch and the chunk defining
`vaadin-tabs`/`vaadin-tabsheet` was never fetched.

Removal of the stale local cache was recorded during the original diagnosis. Local
build artifacts can be regenerated; their presence is not proof of deployed bundle
freshness. Compare the actual route keys and deployed assembly rather than assuming
the local bundles directory is still empty.

What remains is unverified rather than known-broken: **whether the deployed environment is still running
the old assembly.** Check the deployed app rather than this working copy. The mechanism and the
verification greps are in
[engineering notes](engineering/engineering-notes.md#production-frontend-bundle).

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
session over https, so they cannot be discharged by documentation review. Automated
equivalents were reported passing during the feature work; they have not been rerun
as part of this documentation migration.

## 4. Restore the workspace section's navigation entry — *deliberately withheld*

The section has no drawer entry at present; its routes, layout and permission gate all work. Restoring it
requires an entry in `ContentNavigationProvider` plus a label in both bundles and
corresponding navigation tests. Verify the current `MainViewTest` coverage when
making that change; the earlier `WorkspaceNavigationTest` reference is obsolete.

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

**The hard prerequisite:** `IdentitySecureAuthorizationFacade` hardcodes
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

## 8. Participant calling and clipboard in Telegram on iOS — unresolved verification

The user reports that server-delayed `window.location.href = "tel:..."` does not
launch dialing in the iOS Mini App. `WorkspaceParticipantsView` retains the
`callContact` method but comments out its Call control; the rendered screen offers
Show phone only. The old specification's two-action claim is therefore inaccurate.

A native client-side telephone link with the phone available before the tap was
suggested, not implemented or device-verified. Do not add another call dialog or
an extra reveal-dialog button as an assumed solution. Any solution must preserve
the permission-checked contact-disclosure boundary.

Copy uses Vaadin 25.2's browser-click clipboard binding with localized failure
feedback. That API wiring is not an iOS device acceptance result. Verify copy success,
denial/manual-copy feedback, and any separately approved dialing solution on-device.
The Call interaction tests are also commented out. Revisit their coverage alongside
any approved dialing change; this migration does not change tests or behavior.

## 9. Participant descriptor size refusal — service/contract discrepancy

The old specification and converter comment described two size checks. Inspection
finds the sealed-byte cap in `ParticipantDescriptorConverter`, but no corresponding
check in `WorkspaceParticipantServiceImpl`; its sanitizer checks label and phone.
The current logic contract documents converter enforcement rather than promising a
service-level localized size refusal. Decide whether a separate service refusal is
needed, with unit and persistence/integration tests if behavior changes.

## 10. Event count workspace isolation — service query gap

`WorkspaceEventRegistrationServiceImpl.countByEvents` authorizes the supplied
workspace, but `WorkspaceEventRegistrationRepository.countByEventUniqueIds` filters
only by event identifiers. The current UI supplies a workspace-scoped page, but the
service does not constrain arbitrary identifiers to the authorized workspace. An
authorized caller with another workspace's event identifier could obtain its count.

Scope the query or validate all identifiers in a separate logic change, with tests
for foreign/mixed workspace identifiers, normal counts, and absent events. No query,
authorization, or API behavior is changed by the documentation migration.
