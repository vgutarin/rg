# Feature Specification: Workspace Layer

**Feature Branch**: `003-workspace-layer`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Add workspace layer. User can have some workspace to work with. Inside workspace we will keep inherited objects. Like Contacts, Groups, Locations (not global ones)."

## Overview

Today every saved location lives in a single **global** collection that all permitted users share (see
[../current/geolocation.md](../current/geolocation.md)). This feature **replaces** that model with a
**workspace** — a named container a user works inside — and the notion of a **workspace-scoped
(inherited) object**: an object that belongs to exactly one workspace and **derives its access from
that workspace** rather than carrying access rules of its own.

A workspace gives the user a private area of the product: the objects they create inside it are visible
only within it, are listed and searched only within it, and disappear with it. The one object type placed
inside a workspace here is **Locations**; further types (Contacts and Groups are the anticipated next
ones) are added later, so the layer is built to take a new type without reworking it.

**There is no global scope after this feature.** The global location collection is migrated into
workspaces and then removed, along with its navigation entry and its screens: locations exist only inside
a workspace, and location create/read/update/delete moves under the workspace section. Because the old
collection was *shared* — every permitted user could see every location — the migration gives each
distinct author their own default workspace containing a **complete copy** of the collection, so no
migrated user loses sight of anything they could previously reach. After the migration those copies are
independent records.

The whole layer is **opt-in by permission**: only a user holding **`workspace:owner`** has workspaces at
all, and since locations now live only inside workspaces, that permission gates reaching locations at all.
For everyone else the feature is invisible — no workspace navigation item, no workspace provisioned, and
every workspace route denied. Workspaces live in their own **workspace navigation section**, and the
active-workspace selector appears only while the user is inside that section.

Capability permissions are split to match: app-wide permissions stay in the existing declaration, while
permissions that only mean something **inside** a workspace — the location and workspace capabilities —
move to a separate *local* declaration.

"Inherited" is the central rule of this layer: a workspace-scoped object has **no access rules of its
own**. Every check is handed the identifier of the resource being acted upon, resolves that identifier
upward to the workspace containing it, and answers two questions — does the user hold `workspace:owner`,
and do they **own** that workspace. If both hold, the operation is permitted: **ownership grants complete
authority** over the workspace and everything inside it, at any depth, without the user having to hold the
operation's individual capability. The capability is still declared at every call site and validated as a
recognized local permission, but it is *formal* in this feature — it exists so that granular non-owner
access can arrive later without touching a single call site. There are no per-object grants, so there is
one auditable decision point per workspace.

## Clarifications

### Session 2026-09-02

- Q: Which object types does this feature deliver inside a workspace? → A: **Locations only.** Further
  inheritable object types (Contacts, Groups) are added later, so the layer MUST accept a new type without
  changing its access rules. *Affects*: FR-010, FR-017, Key Entities, Scope.
  *(The second half of this answer — that global locations are kept as well — is **superseded** by the
  "global scope removed" session below.)*
- Q: Can a workspace be shared with other users? → A: **No, not for now.** A workspace is strictly
  single-owner and private in this feature; membership, invitations, and sharing are out of scope and no
  requirement assumes them. *Affects*: FR-003, FR-014, Scope.
- Q: How many workspaces may a user have? → A: **Multiple** — one auto-provisioned, non-deletable default
  personal workspace plus additional ones up to a configured bound, with exactly one active at a time.
  *Affects*: FR-002, FR-004, FR-008, FR-009.
- Q: Who may use workspaces at all? → A: **Only users holding the `workspace:owner` permission.** A single
  permission gates the whole layer and the whole workspace lifecycle; without it no workspace is provisioned
  and every workspace operation is denied. *Affects*: FR-001, FR-002, FR-013, FR-028, FR-029, FR-031,
  FR-033.
- Q: Where is the workspace navigation item shown? → A: **Only when the user holds `workspace:owner`**, and
  its existence is not disclosed otherwise. Hiding it is presentation only — the permission is enforced on
  every operation regardless. *Affects*: FR-030, FR-031.
- Q: Where is the active-workspace selector shown? → A: **Only while the user is navigating inside the
  workspace navigation section**, never anywhere else in the application. *Affects*: FR-005, FR-032,
  SC-010.

### Session 2026-09-02 (planning)

- Q: How deep can containment go, and does the workspace owner's authority reach all of it? → A:
  **Arbitrarily deep, and yes.** Objects may nest (workspace → group → event → comment); the workspace
  owner has complete authority over every object in the chain no matter how many levels intervene.
  Resolution walks up from any object to its workspace root. *Affects*: FR-013, FR-014, FR-034 – FR-036,
  SC-015, SC-016.
- Q: Can containment be resolved from an identifier alone? → A: **Yes** — identifiers are unique across
  all resource types (a single central generator issues them), so an identifier names at most one object
  and its type need not be supplied. *Affects*: FR-035.
- Q: What happens when a chain cannot be resolved? → A: **Deny.** An unknown identifier, a chain that
  exceeds the depth bound, and a chain whose root is not a workspace all deny rather than fall back to a
  permission-only check, so a missing containment record can never widen access. *Affects*: FR-036,
  SC-016.

### Session 2026-09-02 (global scope removed)

This session **reverses** the earlier decision to keep global locations (Session 2026-09-02, D1).

- Q: Do global locations survive? → A: **No.** A new workspace-scoped location store is created with a
  mandatory workspace reference, the existing global collection is migrated into workspaces, and the
  global store, its navigation entry, and its screens are then removed. Location create/read/update/delete
  moves under the workspace path. *Affects*: FR-018 – FR-020, FR-037 – FR-041, Key Entities, Scope,
  SC-004, SC-014, SC-017 – SC-019.
- Q: How is the shared collection partitioned without losing access? → A: **Full copy per author.** Every
  distinct author in the existing collection gets a default workspace containing a copy of **all** existing
  locations, because the old collection was shared and any narrower split would remove access a user
  previously had. Duplication and post-migration divergence are accepted consequences. *Affects*: FR-038,
  SC-017.
- Q: How are location capability permissions declared? → A: In a **separate local-permission
  declaration**, distinct from app-wide permissions. Location capabilities move there; `workspace:owner`
  stays app-wide. The scoped authority check accepts only local permissions and the flat check only
  app-wide ones. *Affects*: FR-042 – FR-044, SC-020.

### Session 2026-09-02 (ownership overrides; one check; permission-dispatched resolution)

- Q: Must a workspace owner also hold the individual capability permission? → A: **No.** Resolution reaches
  the workspace anyway, so once the acting user is found to **own** it the declared permission value is
  **ignored** — ownership grants complete authority over the workspace and everything in it at any depth.
  The permission stays at every call site and is validated as a recognized local permission, but is
  *formal* until granular non-owner access exists. *Affects*: FR-013 – FR-015, SC-006, SC-020, and the
  "owner without individual capabilities" edge case (which reverses an earlier one).
- Q: Is there a separate workspace-only authority check? → A: **No — one check only.** The earlier
  `hasWorkspaceAuthority(UniqueId)` proposal is dropped. A single resource-scoped check serves everything,
  which is possible because workspace capabilities are now *local* permissions too. *Affects*: FR-045.
- Q: What identifier does a caller pass? → A: **The identifier of the resource being acted upon**, not the
  containing workspace — for a location update it is the workspace-location's identifier. Resolution finds
  the workspace. Only an operation with no resource yet (creating inside a workspace) passes the container.
  *Affects*: FR-045.
- Q: How is the workspace found from a resource identifier? → A: **The declared permission names the
  type.** Its resource part (`location:…`, `workspace:…`) selects that type's own single-query workspace
  lookup, which joins through however many levels separate the type from the workspace. No probing, no
  runtime traversal, and therefore no depth bound. Warn-log when an identifier is not resolvable. This
  replaces both the containment-index table and the probe loop, which are **dropped**. *Affects*: FR-035,
  FR-036, FR-048, Key Entities, SC-011, SC-015, SC-016, SC-023.
- Q: Do workspace CRUD operations get local permissions? → A: **Yes** — `LocalPermissions.Workspace`
  create/read/update/delete, declared and passed formally now, used for real when non-owner access arrives.
  *Affects*: FR-042.

### Session 2026-09-02 (clarification)

- Q: Is the former global location table deleted by this feature? → A: **No — retained and marked unused.**
  Its navigation entry and screens go, and no production code path reads it, but the table itself stays in
  place with a comment naming its replacement. Dropping it is a later change made after the migration is
  verified against real data. This makes the rollback durable and removes any need to sequence a drop
  against the migration. *Affects*: FR-019, FR-037, FR-041, SC-019.
- Q: What name does an auto-provisioned default workspace get, given that stored names cannot be
  localized? → A: **No stored name — a localized label instead.** A system-created workspace stores no
  name and is marked as the default; the UI renders its label from a message key, so it follows the
  viewer's locale. Naming or renaming it stores the user's text, which then displays verbatim.
  *Affects*: FR-001, FR-006, FR-046, Key Entities, SC-009, SC-021.
- Q: How much data may the one-time migration copy in a single run, and how does it behave at scale? → A:
  **One transaction, all-or-nothing.** The whole migration commits once or not at all; there is no
  batching, no per-author commit, and no total cap. A failure rolls everything back, which makes a
  partially populated workspace structurally impossible. Accepted cost: a long-running transaction and a
  startup that blocks for its duration on a large dataset, with the migration toggle as the operator's
  escape hatch. *Affects*: FR-040, FR-041, FR-047, SC-022.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Work inside a workspace (Priority: P1)

An authenticated user holding `workspace:owner` sees the workspace navigation item, opens it, and is
already inside a workspace — their default personal workspace, created for them on first entry so they are
never without one. The active-workspace selector names it, and appears throughout this section only. The
user creates a location inside it. The location appears in that workspace's list and nowhere else — there
is no other place a location can be.

**Why this priority**: This is the whole point of the layer and the smallest slice that delivers value.
With only this story implemented a user has a working private area with real content in it, and the
containment rule ("belongs to exactly one workspace") is demonstrated end to end.

**Independent Test**: Sign in as a permission-holding user who has never used the application, confirm the
workspace navigation item is present, confirm a default workspace exists and is active, create a location
inside it, and confirm it is listed in that workspace and absent from a second workspace. Delivers a
usable private area without any other story.

**Acceptance Scenarios**:

1. **Given** an authenticated user holding `workspace:owner` who has no workspace yet, **When** they enter
   the workspace navigation section, **Then** a default personal workspace is provisioned for them, becomes
   the active workspace, and is named by the selector.
2. **Given** a user with an active workspace and the create permission for the object type, **When** they
   create a workspace-scoped object, **Then** the object is stored inside the active workspace and is
   listed in that workspace's view.
3. **Given** a workspace-scoped object created in workspace A, **When** workspace B is listed, **Then** the
   object is absent from it, and no path exists to list the object outside a workspace.
4. **Given** a user with an active workspace but without the create permission for the object type,
   **When** they attempt to create a workspace-scoped object, **Then** the attempt is denied with a
   localized message and nothing is stored.

---

### User Story 2 - Keep workspaces isolated and switch between them (Priority: P2)

The user creates a second workspace and switches the active workspace to it. Everything workspace-scoped
they see — lists, searches, detail views, counts — changes over to the newly active workspace in a single
action. Content from the previously active workspace is no longer reachable while the other workspace is
active, and the selection is remembered the next time the user returns.

**Why this priority**: Isolation is only observable once more than one workspace exists, and the whole
value of a container is that its contents do not bleed. This story is what makes the layer trustworthy,
but User Story 1 is already useful without it.

**Independent Test**: Create two workspaces with different content, switch between them, and confirm each
switch shows exactly one workspace's content, that a search in one never returns the other's objects, and
that the selection survives signing out and back in.

**Acceptance Scenarios**:

1. **Given** two workspaces each containing workspace-scoped objects, **When** the user activates the
   second workspace, **Then** every workspace-scoped list and search shows only the second workspace's
   objects and none from the first.
2. **Given** a workspace-scoped object that lives in workspace A, **When** the user has workspace B active
   and requests that object directly by its identifier, **Then** access is denied without revealing
   whether the object exists.
3. **Given** a user who selected a workspace, **When** they end the session and return later, **Then** the
   same workspace is active again.
4. **Given** a blank search inside a workspace, **When** results are returned, **Then** they are bounded
   and contain only that workspace's objects.

---

### User Story 3 - Access is gated by permission and granted by ownership (Priority: P3)

The workspace layer exists only for users holding `workspace:owner`; for anyone else there is no workspace
navigation item, no workspace, and no way in. For those who do hold it, a workspace-scoped object carries
no access rules of its own: **owning the workspace grants complete authority** over it and everything
inside it, at any depth, whatever the object type — the owner needs no further per-capability grant. A user
who does not own the workspace cannot read, change, or remove anything inside it.

**Why this priority**: This is the rule that makes the layer safe and extensible — new inheritable object
types inherit the same behaviour for free, and the gate keeps the feature invisible to users it is not for.
It is testable on its own, but it needs at least one contained object type to exist first (User Story 1).

**Independent Test**: Attempt every workspace operation as a user without `workspace:owner` (including by
navigating straight to a workspace route) and confirm each is denied and the navigation item is absent;
then, for each inheritable object type, attempt every operation as a permission-holding user who does not
own the workspace and confirm every attempt is denied; then confirm the owner succeeds on every operation
**while holding none of the individual capabilities**.

**Acceptance Scenarios**:

1. **Given** an authenticated user who does not hold `workspace:owner`, **When** they view the application's
   navigation, **Then** no workspace navigation item is shown and nothing discloses that the section exists.
2. **Given** an authenticated user who does not hold `workspace:owner`, **When** they navigate directly to a
   workspace route or reference a workspace or contained object by identifier, **Then** the attempt is denied
   with a localized message that discloses nothing about existence, and no workspace is provisioned for them.
3. **Given** a workspace owned by another user, **When** any user who does not own it attempts to read,
   list, search, create, update, or delete objects inside it, **Then** every attempt is denied with a
   localized message that does not disclose the workspace's existence or contents.
4. **Given** a user who owns a workspace and holds `workspace:owner` but holds **none** of the local
   capability permissions, **When** they attempt any operation on any object inside that workspace, **Then**
   every attempt succeeds, because ownership grants complete authority (FR-014).
5. **Given** a user whose `workspace:owner` permission is revoked after they had workspaces with content,
   **When** they use the application afterwards, **Then** the workspace navigation item and selector are
   gone and every workspace operation is denied, while their stored workspaces and contained objects are
   retained and become reachable again if the permission is granted again.
6. **Given** a user who holds `workspace:owner` and owns the workspace, **When** they perform the
   operation, **Then** it succeeds and is recorded with their abstract identity as author or last editor.
7. **Given** an operation on an object contained by a workspace, **When** the check is made, **Then** it is
   made by passing **that object's own identifier** — the caller never resolves or passes the containing
   workspace itself — and the containing workspace is found by the system (FR-045).
8. **Given** a new inheritable object type added to the workspace layer, **When** its access is evaluated,
   **Then** it is decided by the same two conditions — `workspace:owner` and workspace ownership — with no
   type-specific access rule and no per-object grant.

---

### User Story 4 - Manage the workspace lifecycle (Priority: P4)

The user lists their workspaces, renames one, and removes one they no longer need. Removal warns
explicitly that everything inside the workspace goes with it and requires confirmation. The user can
never end up with no workspace at all, and no other workspace is touched by a removal.

**Why this priority**: Housekeeping. Valuable, and it is where the most destructive action in the feature
lives, but a user can work productively with the first three stories in place.

**Independent Test**: Create a workspace, rename it, add content, delete it with confirmation, and verify
its content is gone, the default workspace survives, another workspace becomes active, and a second
workspace's content is unchanged.

**Acceptance Scenarios**:

1. **Given** a workspace the user owns, **When** they rename it, **Then** the new name is stored and shown
   wherever the active workspace is identified.
2. **Given** a workspace containing objects, **When** the user requests its removal, **Then** a
   confirmation step states that the contained objects will be removed, and nothing is removed until the
   user confirms.
3. **Given** a confirmed workspace removal, **When** it completes, **Then** the workspace and every object
   inside it are no longer reachable, and every other workspace's content is unchanged.
4. **Given** the user removes the workspace that was active, **When** the removal completes, **Then**
   another workspace the user owns becomes active and is named on screen.
5. **Given** the default personal workspace, **When** the user attempts to remove it, **Then** the attempt
   is refused with a localized explanation.
6. **Given** two concurrent changes to the same workspace, **When** the second one is saved against stale
   state, **Then** it is rejected with the localized "reload and retry" guidance and no change is silently
   discarded.

---

### Edge Cases

- **First-ever use**: a permission-holding user with no workspace must never see an empty or broken
  workspace screen — the default personal workspace is provisioned before the screen renders.
- **Permission absent**: a user without `workspace:owner` sees no workspace navigation item, has no
  workspace provisioned, and is denied on any direct workspace route or identifier. Because locations now
  live only inside workspaces, such a user reaches no locations at all; the remaining app-wide areas they
  have permission for keep working unchanged.
- **Permission revoked while content exists**: the navigation item and selector disappear and every
  workspace operation is denied, but no workspace or contained object is deleted; granting the permission
  again restores access to the same content.
- **Permission revoked mid-session**: a workspace screen already open must fail safely on the next action
  with a localized message, rather than continuing to serve workspace content from stale state.
- **Owner without individual capabilities**: a user holding `workspace:owner` but none of the local
  location capabilities has full access to their own workspace's locations — ownership overrides the
  capability (FR-014). The capability is still declared at the call site and validated as recognized.
- **Identifier of an unregistered or orphaned resource**: an identifier matching no known workspace-scoped
  type, or whose parent chain does not reach a workspace, denies **and** emits a warning, so the gap is
  visible in operations rather than silently swallowed (FR-036).
- **Selector outside the section**: navigating from the workspace section to a remaining app-wide area
  removes the active-workspace selector; returning restores it with the same active workspace still
  selected.
- **Workspace limit reached**: creating one more workspace is refused with a localized message stating the
  limit; existing workspaces stay usable.
- **Cross-workspace direct access**: requesting a workspace or a contained object by identifier while it
  belongs to a different workspace (or another user) is denied without disclosing existence.
- **Access lost mid-session**: a workspace removed in one place while a workspace-scoped view is open
  elsewhere must produce a clear recoverable state on the next action, not a stale or partially populated
  view.
- **Concurrent workspace switch**: two sessions of the same user selecting different active workspaces
  resolve to one deterministic active workspace, and every workspace-scoped view states which one is
  active rather than assuming.
- **Removal of a workspace mid-operation**: an in-flight create or update targeting a workspace that has
  just been removed fails safely with a clear message and stores nothing.
- **Empty workspace**: a workspace with no contained objects shows an empty state with the action to add
  the first one, not an error.
- **Same content in two workspaces**: an identical name (and, for locations, identical coordinates) may
  exist in several workspaces at once — the migration deliberately creates exactly this situation; the
  layer never deduplicates, merges, or links such records, and proximity suggestions never look outside
  the active workspace.
- **User who authored nothing**: a `workspace:owner` holder who created no location before the migration
  receives no migrated workspace, and gets an empty default workspace on first entry instead. Content the
  former shared collection would have shown them is not reachable — an accepted consequence of moving from
  a shared collection to private workspaces.
- **Migration not yet run or rolled back**: because the migration is atomic (FR-047), there is no partial
  state to present. An author entering before it runs receives an auto-provisioned empty default workspace
  and the ordinary empty state; when the migration later runs it reuses that workspace rather than creating
  a second, so the user simply finds it populated.
- **Free-text naming**: a workspace name or description is the user's own content, stored as given, with
  localized guidance discouraging entry of other people's personal data — the same treatment as location
  free text.
- **Long names in narrow viewports**: a workspace name at its maximum length must remain readable and must
  not break the layout on the narrowest supported screen, in any declared locale.

## Requirements *(mandatory)*

### Functional Requirements

**Workspace container**

- **FR-001**: System MUST let a user holding the workspace permission create a workspace with a required
  name and an optional description, both length-bounded, rejecting invalid input with localized validation
  messages. A **user-supplied** name is required and stored verbatim; see FR-046 for system-created
  workspaces, which store no name.
- **FR-002**: System MUST provision exactly one default personal workspace for a user holding the workspace
  permission, on their first entry into the workspace navigation section, so that no permission-holding
  user is ever without an accessible workspace. System MUST NOT provision a workspace for a user who does
  not hold the permission.
- **FR-003**: System MUST let a user list the workspaces they own and MUST NOT include in that list any
  workspace owned by another user.
- **FR-004**: System MUST maintain exactly one active workspace per permission-holding user, MUST let the
  user change it in a single action, and MUST persist the selection across sessions.
- **FR-005**: System MUST identify the active workspace on every screen inside the workspace navigation
  section.
- **FR-006**: System MUST let a user update a workspace they own (name, description) using optimistic
  concurrency, rejecting a stale save with localized "reload and retry" guidance rather than discarding an
  update. Naming a system-created workspace MUST be permitted and MUST replace its localized label with the
  stored text from then on (FR-046).
- **FR-007**: System MUST let a user remove a workspace they own only after an explicit confirmation step
  that states the contained objects will be removed, and MUST remove every contained workspace-scoped
  object as part of that removal.
- **FR-008**: System MUST refuse removal of the default personal workspace, and MUST ensure that after any
  successful removal the user still has an accessible workspace, activating another one when the removed
  workspace was active.
- **FR-009**: System MUST enforce an explicit, configurable upper bound on the number of workspaces per
  user and refuse creation beyond it with a localized message.

**Inheritance and containment**

- **FR-010**: System MUST declare explicitly which object types are workspace-scoped (inheritable), and the
  declaration MUST be extensible. The declared set in this feature is **Locations** alone; a later feature
  adds further types to the same declaration without altering it.
- **FR-011**: System MUST bind every workspace-scoped object to exactly one workspace at creation time,
  and MUST reject any workspace-scoped object that has no workspace.
- **FR-012**: System MUST scope every read, list, search, and count of workspace-scoped objects to a single
  workspace, and MUST NOT return objects belonging to any other workspace.
- **FR-013**: System MUST derive access to a workspace-scoped object from exactly two enforced conditions,
  both of which MUST hold — either one missing MUST deny:
  1. the acting user holds the app-wide `workspace:owner` permission, which grants *use of the workspace
     layer* (FR-028);
  2. the acting user **owns** the workspace that contains the object — transitively, at any depth
     (FR-014, FR-034).

  System MUST NOT support per-object access grants or type-specific access rules.
- **FR-014**: **Workspace ownership grants complete authority.** When the acting user owns the resolved
  workspace, System MUST permit the operation on the workspace and on every object it contains at any
  depth — read, list, search, create, update, and delete alike — **without** requiring the user to hold the
  operation's local capability permission. System MUST deny every operation to any user who does not own
  the resolved workspace.
- **FR-015**: Every workspace-scoped operation MUST declare the local capability permission it represents,
  and System MUST reject an operation whose declared permission is not a recognized **local** permission.
  The declared value serves two purposes: it **identifies the resource's type and the lookup to run**
  (FR-035, FR-048), which is load-bearing; and it records the capability the operation represents, which is
  **formal in this feature** — validated as declared but not required to be *held*, because ownership
  already grants complete authority (FR-014). The second purpose exists so that granular non-owner access,
  which does not exist yet, can be introduced later without changing any call site.
- **FR-016**: System MUST NOT allow a workspace-scoped object to move between workspaces in this feature.
- **FR-017**: System MUST let a new inheritable object type adopt this layer without changing the access
  rules in FR-013 through FR-015. Adding a type MAY require registering how that type resolves to its
  parent (FR-035), but MUST NOT require any change to who is permitted what.
- **FR-034**: Containment MUST be transitive and depth-independent. A workspace-scoped object MAY be
  contained by another workspace-scoped object rather than by the workspace directly, forming a chain of
  any length (for example workspace → group → event → comment). System MUST resolve any object in such a
  chain to the workspace at its root and MUST apply FR-013 to that workspace, giving the workspace owner
  complete authority over every object in the chain regardless of how many levels lie between.
- **FR-035**: System MUST derive the resource's type from the **declared permission** rather than by
  probing stores: the permission's resource part (`location:…`, `workspace:…`) identifies which type the
  identifier belongs to, and System MUST dispatch to that type's own workspace lookup. Each type MUST
  resolve to its workspace in a **single query**, joining through however many levels separate it from the
  workspace, so resolution cost does not grow with depth. Adding a type MUST require only registering that
  type's lookup, never a change to the access rules (FR-017).
- **FR-036**: System MUST deny — never allow — when no registered type claims the declared permission, when
  the identifier has no row in the dispatched type's store, or when the row does not resolve to a workspace.
  System MUST log a warning carrying only identifiers and the declared permission whenever an identifier is
  not resolvable to a workspace, so an unregistered or orphaned resource is visible in operations rather
  than silently denied. Because resolution performs no runtime traversal, no depth bound is required.
- **FR-048**: The declared permission's **verb** MUST select which lookup runs, so that a single check
  serves every case without the caller resolving anything. Each verb MUST be classified as either:
  - **container-addressed** — the identifier names the container. Covers `create`, which has no resource
    yet, and `list`, whose subject is a collection scoped to its container (listing, searching, and
    proximity matching within a workspace). Applies only to **contained** types: a workspace is a chain
    root, so its own `create` has no container and is not classified here.
  - **resource-addressed** — the identifier names the resource itself. Covers `read` (one item),
    `update`, and `delete`.

  The classification MUST be declared alongside the permissions rather than decided at each call site, so
  a caller cannot pair a permission with the wrong kind of identifier. Each type MUST supply both lookups,
  and each MUST be a single query.

**Removal of the global scope**

- **FR-018**: System MUST store workspace-scoped locations in a dedicated store whose workspace reference
  is **mandatory**, so that a location without a workspace cannot exist.
- **FR-019**: System MUST NOT retain a reachable global location scope after this feature. The former
  global store's navigation entry and screens MUST be removed, and every location capability — create,
  read, update, delete, search, and proximity suggestion — MUST be reachable only inside a workspace. The
  former store's **table is retained**, marked unused, and removed by a later change (FR-037).
- **FR-020**: System MUST make the active workspace unambiguous on every location screen, so a user always
  knows which workspace's locations they are looking at.
- **FR-037**: System MUST migrate the existing global locations into workspaces, and MUST leave the former
  global table in place afterwards — **marked unused** and read by nothing. Removing that table is
  explicitly **out of scope** for this feature and happens in a later change once the migration has been
  verified in the deployed environment. Retaining it is what makes this feature's data change reversible.
- **FR-038**: The migration MUST derive its set of users from the distinct authors recorded on existing
  global locations, MUST create one default workspace per distinct author, and MUST copy **every** existing
  global location into **each** of those workspaces. Rationale: the former collection was shared, so any
  narrower assignment would remove access a user previously had.
- **FR-039**: Each migrated location MUST be a distinct, independent record with its own identifier and its
  own containment binding; migrated copies MUST NOT remain linked to one another, and editing one MUST NOT
  affect another.
- **FR-040**: The migration MUST be idempotent — running it again after a successful run MUST NOT create
  duplicate workspaces or duplicate copies — and MUST preserve each location's business content (name,
  description, coordinates, Google Place ID) and its original author attribution.
- **FR-041**: The migration MUST be recoverable: the original data MUST remain intact and unmodified in the
  retained table, and a failed migration MUST leave **no** trace — no workspace, no copied location, and no
  completion marker — so that no user can ever see a partially populated workspace.
- **FR-047**: The migration MUST execute as a **single atomic unit**: it either completes in full and
  records its completion marker, or it fails and leaves the database exactly as it was. It MUST NOT commit
  partial progress, MUST NOT batch or checkpoint, and MUST NOT impose a cap on the number of authors or
  locations it processes. System MUST provide a configuration switch that disables the migration, so an
  operator facing a dataset too large to migrate at startup can defer it without modifying code.

**Local and app-wide permission declarations**

- **FR-042**: System MUST declare capability permissions that only apply inside a workspace in a
  **local-permission** declaration, separate from the app-wide declaration that holds `workspace:owner` and
  the other application-scoped permissions. The local declaration MUST cover the **location** capabilities
  (read, list, create, update, delete) and the **workspace** capabilities (create, read, update, delete).
  A collection read MUST have its own verb, distinct from reading a single item, because the two address
  different kinds of identifier (FR-048).
- **FR-043**: The resource-scoped authority check MUST accept only local permissions, and the flat
  (app-wide) authority check MUST accept only app-wide permissions. Passing a permission of the wrong kind
  to either check MUST deny. This validation is what keeps the formal declared permission (FR-015)
  meaningful even while it is not required to be held.
- **FR-044**: Both declarations MUST share one permission-syntax rule and MUST each reject an unrecognized
  or malformed value, so splitting the declaration does not weaken validation.
- **FR-046**: A workspace created by the system rather than by a user — the auto-provisioned default
  (FR-002) and every workspace the migration creates (FR-038) — MUST store **no** name, and MUST be
  identifiable as system-named. System MUST render such a workspace's label through the
  internationalization mechanism so it follows the viewer's locale, and MUST NOT store a name in any single
  language. Once a user supplies a name, System MUST store it and display it verbatim thereafter, in every
  locale.
- **FR-045**: System MUST express every workspace-scoped authority decision through a **single** check that
  takes the identifier of the resource being acted upon plus the declared local permission. System MUST NOT
  add a separate workspace-only check, and MUST NOT require callers to resolve or pass the containing
  workspace themselves: for an operation on a contained object the caller passes **that object's**
  identifier, and resolution finds the workspace.

**Safety, privacy, and presentation**

- **FR-021**: System MUST NOT persist, derive, or expose personal data about natural persons anywhere in
  the workspace layer. Should a later inheritable type reference another person, it MUST reference only an
  opaque abstract user identity supplied by the secure service and MUST NOT store that person's name,
  label, nickname, or any contact detail.
- **FR-022**: System MUST record the acting user's abstract identity as author and last editor on
  workspaces and workspace-scoped objects for auditing only, and MUST NOT use those values for access
  decisions.
- **FR-023**: System MUST fail denied access safely: a localized message with no internal detail, and no
  disclosure of whether the requested workspace or object exists.
- **FR-024**: System MUST show localized guidance in workspace free-text fields discouraging entry of
  other people's personal data, while storing the entered text as the user's own content.
- **FR-025**: System MUST resolve all workspace user-facing and assistive text — names of actions, titles,
  validation, empty states, errors, retry guidance, accessibility labels, confirmations — through the
  application's internationalization mechanism, with a deterministic default-locale fallback.
- **FR-026**: System MUST design every workspace flow for narrow mobile viewports first and MUST provide
  clear loading, empty, error, and retry states, preventing accidental duplicate or destructive actions.
- **FR-027**: System MUST keep workspace and workspace-scoped operations out of the presentation layer's
  business responsibility: business rules for containment, inheritance, and lifecycle belong to the
  business-logic module and are invoked by the UI through explicit interfaces.

**Permission gating and navigation**

- **FR-028**: System MUST gate the entire workspace layer on a single `workspace:owner` permission. A user
  who does not hold it MUST NOT be able to create, list, select, rename, or remove a workspace, MUST NOT
  reach any workspace-scoped object, and MUST NOT have a workspace provisioned for them.
- **FR-029**: System MUST declare `workspace:owner` in the application's recognized permission set,
  following the existing `resource:verb` naming convention, so that an unrecognized or malformed value is
  rejected exactly as other permissions are.
- **FR-030**: System MUST show the workspace navigation item only to users holding `workspace:owner`, and
  MUST NOT disclose its existence to users without it. `workspace:owner` MUST be the **only** gate on the
  section and its entries: System MUST NOT additionally gate a workspace screen on a local capability,
  because ownership already grants complete authority over the workspace's contents (FR-014), so such a
  gate would hide a screen the user can in fact use.
- **FR-049**: System MUST NOT expose a local permission in a sanitised principal permission set, nor in
  any authority collection derived from one. Local permissions are never tested against what a principal
  holds (FR-015), so presenting them as granted would imply an enforcement that does not exist.
- **FR-031**: System MUST enforce FR-028 on every attempted operation independently of navigation
  visibility, so that hiding the navigation item is presentation only and never the access control. A user
  without the permission who reaches a workspace route or identifier directly MUST be denied under FR-023.
- **FR-032**: System MUST present the active-workspace selector only while the user is inside the workspace
  navigation section, and MUST NOT present it anywhere else in the application.
- **FR-033**: System MUST treat revocation of `workspace:owner` as loss of access, not loss of data: the
  navigation item and selector MUST disappear and every workspace operation MUST be denied, while the
  user's stored workspaces and their contained objects MUST be retained and MUST become reachable again if
  the permission is granted again.

### Key Entities *(include if feature involves data)*

- **Workspace**: a named container a single user works inside. Holds a required bounded name, an optional
  bounded description, a flag marking it as the user's default personal workspace, its owning user's
  abstract identity, a version token for optimistic concurrency, audit author/last-editor abstract
  identities, and created/updated timestamps. Owns zero or more workspace-scoped objects.
- **Active workspace selection**: the durable, per-user record of which single workspace is currently in
  context. Exactly one per user; references one workspace the user owns.
- **Workspace-scoped (inherited) object**: the general notion of an object that belongs to exactly one
  workspace and derives its access from it. Relationship: many-to-one to Workspace, mandatory, immutable
  in this feature. Its only member in this feature is Location; the notion exists so later types join it
  without changing the access rules.
- **Workspace location**: a saved location inside a workspace — the only kind of location that exists after
  this feature. Carries the same business content as the former global location (name, optional
  description, optional coordinates, optional Google Place ID, version token, author/last-editor audit
  identities, timestamps) plus a **mandatory** workspace reference. Behaviour otherwise follows
  [../current/geolocation.md](../current/geolocation.md).
- **Local permission declaration**: the set of capability permissions meaningful only inside a workspace —
  the location capabilities. Distinct from the app-wide declaration that holds `workspace:owner`; both
  share one syntax rule.
- **Retired: global location collection**: the shared, workspace-less collection described in
  [../current/geolocation.md](../current/geolocation.md). Migrated into workspaces (FR-037 – FR-041) and
  then removed together with its navigation entry and screens. No global location scope remains.

### Scope

**In scope**: the workspace container and its lifecycle; the `workspace:owner` permission gate and the
permission-conditional workspace navigation item; the workspace navigation section and the
active-workspace selector scoped to it; the active-workspace selection; the containment and inheritance
rules for workspace-scoped objects; applying all of the above to **Locations** as the sole inheritable type
in this feature; **migrating the existing global locations into workspaces and removing the global scope**
along with its navigation entry and screens; **splitting capability permissions into local and app-wide
declarations**; and keeping the declaration of inheritable types extensible so a later feature can add
another type without reworking the access rules.

**Out of scope**: every further inheritable object type, including **Contacts** and **Groups** — each is
specified and delivered separately, and no requirement here depends on one existing; sharing
a workspace with other users, membership, and invitations; per-object access grants and the permission
inheritance/ACL research recorded in `TODO.md`; moving objects between workspaces; deduplicating or
re-linking the copies the migration creates; provisioning workspaces for users who authored nothing (they
receive an empty default workspace on first entry instead); a public space.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero cross-workspace leakage: across every read, list, search, count, and direct-identifier
  access exercised by the test suite, 100% of returned workspace-scoped objects belong to the single
  workspace in context, and 0 belong to any other workspace.
- **SC-002**: A permission-holding user who has never used the application can create their first
  workspace-scoped object in no more than 3 actions after entering the workspace navigation section,
  without first having to create or configure a workspace.
- **SC-003**: Changing the active workspace takes exactly 1 user action and, after it, 100% of visible
  workspace-scoped content belongs to the newly active workspace, with no item carried over.
- **SC-004**: Removing a workspace removes 100% of its contained objects and changes any other workspace's
  contents by 0 items, verified by count before and after.
- **SC-005**: A user who does not own a workspace is denied on 100% of attempted operations against that
  workspace and every object inside it, for every inheritable object type, and no denial message reveals
  whether the target exists.
- **SC-006**: Ownership grants complete authority: a workspace owner holding `workspace:owner` but **none**
  of the local capability permissions succeeds on 100% of operations against their own workspace and its
  contents, at every depth.
- **SC-007**: Every permission-holding user always has an accessible workspace: across all
  workspace-removal paths, the number of such users left with zero workspaces is 0.
- **SC-008**: A privacy review of the workspace layer finds 0 fields, log entries, metrics, or diagnostic
  artifacts holding personal data about natural persons, and 0 places where a referenced person is
  identified by anything other than an opaque abstract identity.
- **SC-009**: Every workspace flow is completed successfully on the narrowest supported viewport without
  horizontal scrolling, and in the default locale plus at least one declared non-default locale, with 0
  untranslated or missing-key strings — including the label of a system-created workspace, which renders in
  the viewer's locale in 100% of locales tested.
- **SC-022**: The migration is atomic: when a run is interrupted or fails at any point, the number of
  workspaces it created is 0, the number of locations it copied is 0, the completion marker is absent, and
  the source table is unmodified — verified by injecting a failure mid-run.
- **SC-021**: No system-created workspace stores a language-specific name: across all auto-provisioned and
  migration-created workspaces, the number carrying a stored name is 0; and after a user names one, the
  number still rendering a localized label instead of the user's text is 0.
- **SC-010**: The active-workspace selector appears on 100% of screens inside the workspace navigation
  section and on 0 screens outside it.
- **SC-011**: Adding a further inheritable object type requires 0 changes to the access rules and 0 changes
  to any existing call site; the only addition is that type's parent lookup registration, demonstrated by
  the access rules being expressed once and exercised per type.
- **SC-012**: Users without `workspace:owner` are denied on 100% of attempted workspace operations,
  including direct route and identifier attempts; the workspace navigation item is shown to 0 of them; and
  0 workspaces are provisioned for them.
- **SC-013**: Revoking `workspace:owner` from a user with existing content deletes 0 workspaces and 0
  contained objects, and re-granting it restores access to 100% of that content.
- **SC-014**: Removing `workspace:owner` from a user changes the behaviour of the remaining app-wide areas
  they still have permission for in 0 observable ways.
- **SC-015**: The workspace owner's authority is depth-independent at constant cost: for a containment
  chain of at least 5 levels below the workspace, 100% of objects in the chain are manageable by the
  workspace owner — passing any one of their identifiers — the number of access rules that had to change to
  support the extra depth is 0, and the number of queries per authority check is **1** regardless of the
  level.
- **SC-016**: Resolution fails closed and is observable: for an unknown identifier, a permission no
  registered type claims, and an identifier whose row does not resolve to a workspace, 100% of authority
  checks return denied, 0 fall back to a permission-only decision, and 100% emit a warning carrying only
  identifiers and the declared permission.
- **SC-023**: Permission and identifier must agree: passing an identifier of one type with another type's
  permission (for example a workspace identifier with a non-create location permission) is denied 100% of
  the time rather than resolving through the wrong store.
- **SC-017**: The migration is complete and lossless: for A distinct authors and L existing global
  locations, exactly A default workspaces exist and each contains exactly L locations (A × L copies in
  total), and 100% of copies match their source's name, description, coordinates, Google Place ID, and
  author attribution.
- **SC-018**: The migration is idempotent: running it a second time creates 0 additional workspaces and 0
  additional location copies.
- **SC-019**: No global location scope is reachable: the count of locations reachable without a workspace is
  0, the number of navigation entries or routes serving a global location collection is 0, and 100% of
  location capabilities are reachable only inside a workspace. The retained former table is read by 0
  production code paths.
- **SC-020**: The permission split is enforced, not merely organizational: passing a local permission to
  the flat authority check and passing an app-wide or undeclared permission to the resource-scoped check are
  each denied 100% of the time — even for a workspace owner, whose ownership overrides whether a permission
  is *held* but never whether it is *declared*.

## Assumptions

- **Existing identity is reused**: workspace ownership and auditing use the opaque abstract user identity
  already supplied by the secure-authorization boundary. The feature introduces no new identity flow and
  resolves no identity to a natural person.
- **Two enforced conditions, one formal**: `workspace:owner` supplies *use of the layer* and ownership of
  the resolved workspace supplies *complete authority* over its contents; both are enforced. The declared
  local capability is validated but not required to be held, because the resolution needed to find the
  workspace already establishes ownership, and a workspace owner having to additionally be granted each
  capability inside their own private workspace would be ceremony without a security benefit while
  workspaces are single-owner. When sharing arrives, the declared capability becomes the mechanism for
  non-owner access — which is why it is passed at every call site now.
- **One check, resource-addressed**: a single check takes the identifier of the resource being acted upon.
  Callers never resolve or pass the containing workspace themselves (except when creating, where no
  resource exists yet), so a call site cannot get the scope wrong by resolving it incorrectly.
- **Resolution is dispatched by the declared permission, not by probing**: the permission's resource part
  names the type and its verb names the lookup, and that type resolves to its workspace in one query. This
  removes the containment-index table (and its "write the index row in the same transaction" invariant),
  the probe loop, and the traversal depth bound — at the cost of requiring the permission and the
  identifier to describe the same resource, which denies on mismatch rather than failing to compile.
- **`workspace:owner` is a single app-wide permission covering the whole workspace lifecycle** — create,
  list, select, rename, remove — rather than a read/create/update/delete split like locations, because only
  one permission name was specified. It stays in the app-wide declaration and follows the existing
  `resource:verb` naming convention.
- **The permission gates access, not data retention**: revoking it hides the feature and denies every
  operation but never deletes a workspace or its contents, since silent data loss on a permission change
  would be destructive and unrecoverable. Re-granting restores access to the same content.
- **Navigation visibility is presentation, never enforcement**: hiding the navigation item is a usability
  measure, and the permission is checked on every operation independently, so a user who reaches a route
  directly is denied identically.
- **The workspace navigation section is the only home for workspace content**: every workspace-scoped
  screen lives inside it, which is what makes "selector only inside the section" a coherent rule rather
  than a special case.
- **The global scope is retired, not preserved**: locations exist only inside workspaces after this
  feature. This reverses the earlier "keep global locations" decision.
- **Full-copy migration is deliberate, and its costs are accepted**: because the former collection was
  shared, each author's workspace receives a complete copy, so the row count becomes authors × locations
  and the copies diverge from one another as soon as anyone edits one. Assigning each location only to its
  own author's workspace would avoid the duplication but would remove access users previously had, which
  is why it was not chosen.
- **Authors are the only enumerable users**: the application deliberately stores no user records, so the
  migration can only discover users through the author field on existing locations. Users who authored
  nothing therefore cannot be provisioned in advance and receive an empty default workspace lazily.
- **The migration needs generated identifiers**, so it runs as application logic against the identifier
  service rather than as pure schema SQL, and the removal of the former store is gated on the migration
  being verified complete.
- **A default personal workspace is auto-provisioned** and cannot be removed, so the product never has to
  present a "no workspace" state.
- **One active workspace at a time**, persisted server-side per user rather than per device, so the
  experience is the same wherever the user signs in.
- **Objects are immovable in v1**: no move between workspaces. Adding it later needs its own specification
  because it changes who can see existing content.
- **No per-object access grants**: the permission-node / inheritance-chain design sketched in `TODO.md`
  remains research. This feature implements a single level of inheritance — object to workspace — which is
  the smallest thing that satisfies the requirement, consistent with the project's simplicity mandate.
- **Forward constraint on a future Contacts type** (out of scope here, recorded so it is settled before that
  type is specified): because the project prohibits persisting personal data about natural persons, a
  Contact can only be an opaque reference to another platform user's abstract identity — not a name,
  nickname, phone number, or address book entry. A user-authored label naming another person would itself
  be personal data and is therefore excluded. This materially shapes what Contacts and Groups can be.
- **Workspace names are business content**, treated exactly like location free text: stored as given, with
  localized guidance discouraging entry of other people's personal data, and no scanning or blocking.
- **Consistent concurrency behaviour**: workspace updates use the same optimistic-concurrency approach and
  the same "reload and retry" guidance already used for locations.
- **Explicit bounds are configurable**: the maximum workspaces per user, the name/description length
  limits, and the containment traversal depth are configuration values with documented defaults, not
  hardcoded assumptions.
- **The permission split is enforced by the checks themselves**, not merely by file organization: the
  resource-scoped check accepts only local permissions and the flat check only app-wide ones, so a
  mis-scoped permission is a denial rather than a silent pass.
- **Application-owned performance only**: workspace flows do not depend on an external service beyond the
  existing authentication boundary, so their acceptance criteria measure application-owned work and do not
  impose end-to-end latency targets on externally dependent paths.
- **The location domain is extended, not replaced**: the existing location behaviour specified in
  [../current/geolocation.md](../current/geolocation.md) continues to apply to workspace-scoped locations
  except where this specification scopes visibility and access to a workspace.
