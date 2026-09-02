# Specification Quality Checklist: Workspace Layer

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-02
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Validation Notes

**Iteration 1 (2026-09-02)** — issues found and fixed before the spec was finalized:

1. *Fabricated clarification record*: an earlier draft presented three scope decisions as a completed
   "Session 2026-09-02" Q&A that had not taken place. Replaced with an **Open Decisions** section that
   labels each as a proposed default and names the requirements it affects.
2. *Implementation leakage into requirements*: draft requirements named concrete permission strings and
   persistence mechanics. Reworded to capability-permission and optimistic-concurrency behaviour without
   naming syntax, tables, or annotations. FR-027 deliberately retains the module-ownership obligation
   because the project constitution makes it a governance requirement rather than a design choice.
3. *Unmeasurable success criteria*: "isolation works" style statements replaced with counted outcomes
   (SC-001, SC-004, SC-005, SC-006, SC-007, SC-008 all state a 0 or 100% target with a verification basis).
4. *Missing bounds*: added FR-009 (workspace count) and FR-001 (name/description length) so every
   user-supplied input has an enforceable limit, as the constitution requires.

**Iteration 2 (2026-09-02)** — the three open scope decisions were confirmed by the requester and the
Open Decisions section became the **Clarifications** record (D1 locations only with global locations kept,
D2 no sharing for now, D3 proposed default). Requirements realigned accordingly:

1. FR-010 reworded: the declared inheritable set is **Locations alone** and the declaration must be
   extensible, rather than pre-declaring Contacts and Groups.
2. Key Entities: Contact and Group entries removed (out of scope); a **Location (global)** entry added to
   make the preserved existing collection explicit.
3. Scope: Contacts and Groups moved into Out of scope, with extensibility of the type declaration named as
   an in-scope obligation. Overview aligned.
4. FR-021 and the Contacts assumption restated as a **forward constraint** on a future type instead of a
   requirement on something this feature delivers.

**Iteration 3 (2026-09-02)** — the requester added the permission gate and the navigation rules. Three
clarifications recorded and the spec extended:

1. New requirement group **Permission gating and navigation** (FR-028 – FR-033): `workspace:owner` gates the
   whole layer and lifecycle; the permission is declared in the recognized permission set; the navigation
   item is permission-conditional; enforcement is independent of navigation visibility; the selector is
   confined to the workspace navigation section; revocation withdraws access without deleting data.
2. FR-013 restated as an explicit **three-condition** access rule (layer permission, workspace ownership,
   object capability), replacing the earlier two-part rule. FR-001, FR-002, FR-004, FR-005 and FR-018
   realigned to it.
3. User Story 3 retitled and extended with the gating scenarios, including direct-route attempts and the
   revocation-retains-data case. Five new edge cases added (permission absent, revoked with content
   present, revoked mid-session, layer permission without object capability, selector outside the section).
4. SC-010 rewritten as a two-sided measurement (selector on 100% of in-section screens, 0% elsewhere);
   SC-012 – SC-014 added for the gate, non-destructive revocation, and no regression to global areas.
5. Requirement groups reordered so FR numbers now run 001 – 033 in document order.

**Two informed decisions recorded in Assumptions** rather than raised as questions, since a reasonable
default existed: `workspace:owner` is a *single* permission covering the whole lifecycle (only one name was
specified, unlike the four-verb location split), and revocation *hides* rather than deletes (silent data
loss on a permission change would be unrecoverable). Both are cheap to change if intended otherwise.

**Iteration 4 (2026-09-02)** — the requester reversed the "keep global locations" decision. The global
scope is retired entirely. Spec reworked and re-validated:

1. New requirement group **Removal of the global scope**: FR-018 – FR-020 rewritten (mandatory workspace
   reference; no global scope; active workspace legible) and FR-037 – FR-041 added for the migration
   (per-author default workspaces, full copy of every location into each, independent records, idempotency,
   recoverability).
2. New group **Local and app-wide permission declarations**: FR-042 – FR-044. FR-045 added after design
   surfaced a contradiction — `workspace:owner` is app-wide, so it cannot pass a scoped check that accepts
   only local permissions; a dedicated workspace-level check resolves it rather than special-casing.
3. SC-001, SC-004, SC-014 de-globalized; SC-017 – SC-020 added for migration completeness, idempotency,
   the absence of any global scope, and enforcement of the permission split.
4. Overview, Scope, Key Entities, user stories, edge cases, and Assumptions swept for stale
   global-coexistence language. Superseded clarification answers are marked as superseded rather than
   silently edited, so the decision history stays readable.
5. Three new edge cases: a user who authored nothing, identical copies across workspaces, and the
   migration-not-yet-complete state.

**Two consequences the requester should confirm** (recorded in Assumptions and the plan rather than
blocking, since the instruction was explicit):

- **A × L fan-out.** Copying every location into every author's workspace multiplies rows and leaves
  copies that diverge once edited. It is the only partition that preserves what each migrated user could
  previously see, because the old collection was shared — but the narrower alternative (each location to
  its own author only) is a one-line change if duplication is the greater concern.
- **Non-author users lose the shared collection.** Authors are the only enumerable user set, since the
  application deliberately stores no user records. A `workspace:owner` holder who never created a location
  gets an empty default workspace on first entry.

**Sequencing constraint found during design** (research R10): the migration needs identifiers from the
central generator, so it must run as application logic — which starts *after* Liquibase. A `dropTable`
in the same changelog would therefore delete the source data before the migration could run. The drop is
consequently a gated follow-up changeset applied on the next deployment, which also supplies the rollback
window the constitution requires for a breaking change.

**Iteration 5 (2026-09-02)** — the requester simplified the access model. Three changes, each of which
*removed* design rather than adding it:

1. **`hasWorkspaceAuthority` dropped** (FR-045 rewritten). Adding `LocalPermissions.Workspace` CRUD gives
   workspace operations local permissions of their own, so one resource-scoped check now covers the
   workspace and everything in it. The contradiction that motivated the second method is gone at the root.
2. **Ownership overrides the declared permission** (FR-013 – FR-015 rewritten; SC-006 **inverted**). Since
   resolution must reach the workspace anyway, ownership is known by decision time; requiring an owner to
   also hold each capability inside their own private workspace is ceremony with no second party to protect
   against. The permission stays at every call site and is validated as *declared*, but is not required to
   be *held* — the seam for granular non-owner access later. The "owner denied without location:read" edge
   case is reversed accordingly.
3. **The containment-index table is dropped** (FR-035/FR-036 rewritten). Resolution now probes each known
   type in turn — workspace first, then each contained type via its own mandatory parent column. This
   removes `rg_resource_node`, its entity, its repository, its cascade rule, and the "write the index row
   in the same transaction" invariant, plus the MySQL-specific recursive SQL. Depth-independence is
   preserved through a per-type `ResourceParentResolver` registration.

Also: FR-045 now states that callers pass the **resource's own** identifier (a location update passes the
workspace-location id), never a workspace they resolved themselves; SC-011, SC-015, SC-016 and SC-020
updated; three assumptions rewritten; R1, R2, R3, R7, R8, R11 revised in research.

**Two things a reviewer should weigh:**

- **Local permissions are declared but not enforced against what a user holds.** This is safe only while
  workspaces are strictly single-owner. Recorded as a hard condition in the plan's Complexity Tracking:
  introducing sharing **must** enable the held-check for non-owners before any non-owner can reach a
  workspace. Nothing in the current design fails if that is forgotten, which is exactly why it is written
  down.
- **The probe costs more queries than the index it replaced** — up to one per registered type per level
  (two today, ~25 at five types and five levels, versus one recursive query). Accepted for now; research
  R3 records the concrete trigger for adding a resolved-workspace cache.

**Iteration 6 (2026-09-02, `/speckit-clarify`)** — one direct instruction plus two clarifying questions:

1. **`rg_location` is retained, not dropped** (FR-019, FR-037, FR-041, SC-019; research R10 rewritten).
   No `dropTable` anywhere; the table stays with a comment naming its replacement, and removal is a later
   change. This *dissolved* the two-deployment sequencing constraint flagged in Iteration 4 — with nothing
   to drop, the ordering between Liquibase and the application-level migration stops mattering, and the
   feature becomes a single self-contained deployment with a durable rather than time-boxed rollback.
2. **System-created workspaces store no name** (new FR-046, SC-021; FR-001 and FR-006 refined). This closed
   a real gap: FR-002 and FR-038 both create workspaces without a user present, FR-001 requires a name, and
   a stored name cannot follow the viewer's locale — which would have violated Principle V. Resolution:
   `name` is nullable, `NULL` means system-named, and the UI renders `workspace.default.name`. A user-
   supplied name is stored and shown verbatim thereafter; the transition is one-way.
3. **The migration is one atomic transaction** (new FR-047, SC-022; FR-040/FR-041 refined). All-or-nothing
   with no batching and no cap. This *strengthened* FR-041 from a behaviour to a structural property —
   a partially populated workspace is now impossible rather than merely avoided — and removed checkpoint
   and resumption logic, along with the "migration in progress" UI state it would have required.
   Costs accepted and recorded: a long transaction, growing undo log, and a startup that blocks for the
   copy on a large dataset, with `rg.workspace.migration.enabled=false` as the operator's escape hatch.

Also fixed in passing: a garbled sentence in `quickstart.md` left by an earlier edit, which had merged a
dropped drop-precondition assertion into the source-rows-unmodified assertion.

**Iteration 7 (2026-09-02)** — the requester replaced identifier-probing with **permission-dispatched
resolution**: the declared permission's resource part names the type, and that type resolves to its
workspace in one query with whatever joins its depth requires.

1. FR-035 rewritten; new **FR-048** (the permission's *verb* selects the lookup); FR-036 rewritten to drop
   the depth bound; FR-015 strengthened — the permission is no longer merely formal, it now carries the
   dispatch key, which is a far better reason to pass it at every call site.
2. New **SC-023** (permission and identifier must describe the same resource); SC-015 now also asserts
   **one query per check** regardless of level; SC-016 reworded for the new failure modes.
3. Naming and placement settled as requested: `WorkspaceScope`, `WorkspaceScopeResolver`(`Impl`), and the
   `WorkspaceScopeProvider` seam in `vg.rg.security`; `WorkspaceSelfScopeProvider` and
   `LocationScopeProvider` in `vg.rg.service`. "Scope" rather than "ancestry"/"parent" because nothing
   walks a hierarchy any more. Providers sit in `service`, not `repository`, because they are components
   with behaviour — `repository` stays purely declarative.
4. `rg.workspace.max-ancestry-depth` **deleted** (T001), along with cycle handling: with no runtime
   traversal there is no loop to bound and no cycle to detect. `LocalPermissions` nested groups gained
   `contains(String)` as the dispatch key.

**A hole in the proposal, found and closed**: dispatching on the resource part alone breaks `create`.
`hasAuthority(#workspaceId, LocalPermissions.Location.CREATE)` passes a *workspace* identifier with a
`location:*` permission, so the dispatch would look it up in the location store, miss, and deny every
location creation. FR-048 resolves it by letting the **verb** choose the lookup — `create` addresses the
container, every other verb addresses the resource — which keeps one method and one query. The three
alternatives (guard creation with the container's permission, fall back to a workspace lookup on a miss,
add a second method) are recorded as rejected in research R1.

**New coupling a reviewer should note**: the permission and the identifier must now describe the same
resource. A mismatch denies (SC-023) rather than failing to compile — safe, but it is a call-site
responsibility that probing did not impose. Quickstart Scenario D adds an explicit read-through for it.

**Iteration 8 (2026-09-02, post-implementation review)** — the requester questioned why the app-wide
declaration unioned in the local one. It did not need to, and the union was actively harmful:

1. Its stated justification — keeping `location:read` in the sanitised set so a navigation gate could read
   it — was **invalidated by the ownership-override decision** (Iteration 5) and I had not propagated that.
   Since owning a workspace grants complete authority over its locations, gating the locations entry on
   `location:read` would have **hidden a screen the user can actually use**.
2. Sanitised permissions become Spring `GrantedAuthority` values, so the union surfaced *formal* local
   permissions as granted — inviting a future `@PreAuthorize("hasAuthority('workspace:update')")` that
   passes on holdings while the real check ignores holdings entirely.
3. It left two meanings of "recognized" in one class (`isRecognized` app-wide, `recognized(...)` spanning
   both) — a trap for the next reader.

Changes: the union is removed (`Permissions.ALL == APP_WIDE`); `Permissions` now references
`LocalPermissions` nowhere, which also removes the class-initialization-cycle risk at the root rather than
working around it. New **FR-030** clause forbids gating a workspace screen on a local capability, and new
**FR-049** forbids exposing a local permission in a sanitised set or derived authority collection. The UI
contract's nav gate and its "owner without `location:read` sees a denial" claim are corrected — an owner
sees the ordinary empty state, never a denial. Research R11 and the data model record the reasoning.

The two tests that encoded the old behaviour were inverted rather than deleted, so the decision stays
asserted: `recognized_purelyLocalPermission_isDropped` and `all_isAppWideOnly`.

**Constitution-driven constraint worth reviewer attention**: because the project prohibits persisting
personal data about natural persons, a future Contacts type can only hold opaque abstract-user references —
no name, nickname, or contact detail. Recorded in the Assumptions so it is settled before Contacts is
specified; it is not a requirement of this feature.

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
