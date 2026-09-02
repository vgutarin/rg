# Contract: Workspace Navigation Section and Selector

**Module**: `rg-frontend-vaadin` | **Spec**: [../spec.md](../spec.md) | **Research**: [../research.md](../research.md) (R5)

Presentation only. This module holds no business rules; it renders state and invokes `rg-logic` interfaces
(Principle VI, FR-027).

## Navigation item — permission-conditional

`MainView.drawer()` gains one item, built exactly like the existing entries:

```java
if (permissions.contains(Permissions.Workspace.OWNER)) {
    var workspaces = addNav(nav, "nav.workspaces", "/workspaces", VaadinIcon.FOLDER_O.create());
    addChildNav(workspaces, "nav.locations", "/workspaces/locations", VaadinIcon.MAP_MARKER.create());
}
```

The locations entry **moves under** the workspace item (FR-019); the old top-level entry is removed.

**`workspace:owner` is the only gate** — the child entry carries no separate `location:read` check. It
would be wrong to add one: ownership grants complete authority over everything inside the workspace
(FR-014), so a user who owns a workspace can use its locations screen whether or not they hold
`location:read`. Gating on it would hide a screen the user can actually use. The local capabilities are
formal in this feature and are never tested against what a principal holds, here or anywhere else.

- Shown only to holders of `workspace:owner` (FR-030). Users without it see no item and nothing that hints
  the section exists.
- `MainView` sanitises the principal's permissions through `Permissions.recognized(...)`, so an
  unrecognized value cannot switch an item on (FR-029). That filter spans the **app-wide declaration
  only**: local permissions are dropped from it deliberately, because nothing checks whether they are
  held and the sanitised set becomes Spring granted authorities (research R11).
- **Visibility is not enforcement.** Every route and every service call re-checks the permission
  independently, so a user who types `/workspaces` is denied identically (FR-031).

## Route structure — the selector's confinement

```text
MainView (AppLayout, @PermitAll)
├── /                     LandingView            — app-wide
├── /reports              ReportsView            — app-wide, reports:read
└── WorkspaceLayout  (@ParentLayout(MainView.class))   ← renders the selector
    ├── /workspaces             WorkspacesView          — list, create, rename, remove
    └── /workspaces/locations   WorkspaceLocationsView  — the only locations screen
```

**`/locations` and `LocationsView` are removed** (FR-019). Locations exist only under the workspace path,
so the former global route must not resolve — no redirect either, since there is no global collection to
redirect to. Removing the route is part of the deliverable, not a follow-up.

`WorkspaceLayout implements RouterLayout` and is the **only** component that renders the
active-workspace selector. Because no other layout contains it, the selector cannot appear outside the
section — FR-032 is structural rather than a rule each view must remember (research R5). Leaving the
section removes the selector; returning restores it with the same active workspace (spec edge case).

`WorkspaceLayout` responsibilities:

1. Deny entry when the user lacks `workspace:owner`, rerouting to the existing `NoAccessView` — the
   pattern `LocationsView` already uses (`@PermitAll` plus `BeforeEnterObserver`).
2. Resolve the active workspace once per navigation via `WorkspaceSelectionService.activeWorkspace()`,
   which provisions the default on first entry (FR-002) and repairs a stale selection.
3. Render the selector: the caller's owned workspaces, current one selected, changing it in **one action**
   (FR-004, SC-003) via `WorkspaceSelectionService.select(...)`, then refreshing the child view.
4. Name the active workspace on every screen in the section (FR-005, SC-010). When the model's `name` is
   `null` the workspace is **system-named**: render a localized label from `workspace.default.name` rather
   than any stored string, so it follows the viewer's locale (FR-046). A workspace with a stored name shows
   that text verbatim in every locale.

## Views

**`WorkspacesView`** (`/workspaces`) — lists owned workspaces; create with a bounded name and optional
description; rename inline; remove behind a confirmation dialog that states the contained objects will be
removed (FR-007) and refuses for the default workspace (FR-008). Renders the workspace-limit refusal
(FR-009) and the stale-save "reload and retry" guidance (FR-006).

**`WorkspaceLocationsView`** (`/workspaces/locations`) — the **only** locations screen, replacing the
removed `LocationsView`. Calls `WorkspaceLocationService` with the active workspace. A workspace owner
reaches it and can use it fully without holding any `location:*` capability, because ownership overrides
them (FR-014); an empty workspace therefore shows the ordinary empty state, never a denial. The two-tab
structure, the Google Maps picker, and `MapsResolutionBridge` are carried
over as specified in [../../current/geolocation.md](../../current/geolocation.md) — including
`MapsResolutionBridge`'s proximity call, which is re-pointed at the active workspace. Only the scope
changes; the geolocation behaviour does not.

**Migration states** — because the migration is all-or-nothing (FR-047) there are only two, and neither
needs special UI:

- *not run* (disabled, or a failed run that rolled back): an author entering the section gets their
  auto-provisioned **empty** default workspace and the normal empty state. That is a correct, complete
  view of what exists — not partial content.
- *complete*: the same default workspace now holds the copied locations. The migration skips creating a
  default for an author who already has one, so a user who arrived before the migration keeps their
  workspace and simply finds it populated.

The UI therefore renders the ordinary empty state, never a "migration in progress" screen.

## Presentation requirements

- **Mobile-first** (Principle V, FR-026): the selector must work in the narrow Telegram webview — it sits
  in the section's own header area rather than competing with `MainView`'s navbar, and a workspace name at
  its maximum length must not break the layout at the narrowest supported width, in any declared locale.
- **States**: loading, empty (a workspace with no locations offers the add action), error, and retry are
  explicit; destructive actions are confirmed and guarded against double submission (FR-026).
- **Scope legibility** (FR-020): every locations screen states which workspace it is showing. Since the
  migration deliberately puts identical copies in several workspaces, the active workspace's name is the
  only thing distinguishing them — so it must be visible, not inferred.
- **i18n** (FR-025): every string resolves through `LocalizationService` with keys in
  `messages.properties` (Ukrainian, the default) and `messages_en.properties`. New keys cover the nav
  item, page titles, the selector label, **the system-named workspace label
  (`workspace.default.name`)**, create/rename/remove actions and confirmations, the limit refusal, the
  default-workspace refusal, validation, denial, and the personal-data guidance for free-text fields
  (FR-024).

## Required test coverage

- The nav item and its child locations entry are present with `workspace:owner` and absent without it
  (FR-030), using the existing `MainView` navigation-label test approach.
- A workspace owner holding **no** `location:*` capability still sees the locations entry and can use the
  screen (FR-014) — the inverse of the gate that was removed.
- No navigation entry or resolvable route serves a global location collection (SC-019), and `/locations`
  no longer resolves.
- `WorkspaceLayout` reroutes a user without the permission and does not provision a workspace for them
  (FR-031, SC-012).
- The selector is present on every route inside the section and absent on every route outside it (FR-032,
  SC-010).
- Switching the workspace in one action changes the child view's content wholly (SC-003).
- Default-locale and one non-default-locale rendering, including a maximum-length workspace name at the
  narrowest supported viewport (SC-009).
- A **system-named** workspace (stored `name` is `null`) renders its localized label in both locales, and
  after the user names it, renders the stored text unchanged in both (SC-021, FR-046).
