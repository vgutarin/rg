# Workspaces

## Purpose and entry

Owners manage workspaces at `/workspaces`. The route is nested in `WorkspaceLayout`
under the authenticated shell and has no drawer entry. Layout entry requires the
app-wide workspace permission; a caller without it is rerouted to `/no-access`.

The same layout hosts `/workspaces/locations`, `/workspaces/participants`, and
`/workspaces/events`. Its active workspace supplies the scope for each child view.
[Workspace lifecycle](../logic/workspace/lifecycle.md) owns provisioning and selection.

## Selection

The full-width selector appears only within the workspace layout and only when the
owner has more than one workspace. A non-default active workspace has a caption
beneath it; the default has no active-workspace caption.

System-named workspaces use the localized default label; stored names display
verbatim. A switch stores the selection, resolves the active workspace again,
refreshes the header, and reloads the page so child content is rebuilt against the
selected workspace. Selection is persistent per user, not confined to the UI session.

## Management and states

Owners list, create, rename/edit, and remove workspaces. Name and description fields
carry localized guidance discouraging other people's personal data. Business
validation and configured bounds are defined in the lifecycle contract.

Removal requires confirmation explaining that contents are also removed. The default
workspace has no remove action. A stale edit produces localized reload/retry guidance
and retains the typed values in the dialog. Validation and limit failures produce
localized feedback without discarding the available workspaces.

## Presentation

[Foundations](foundations.md) govern responsive layout, wrapping, and action styling.
The selector is structurally absent outside this layout. Top-level content navigation
is defined in [navigation](navigation.md), independently of workspace management.

## Evidence

- [WorkspacesView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspacesView.java)
- [WorkspaceLayout.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayout.java)
- [WorkspacesViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspacesViewTest.java)
- [WorkspaceLayoutTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayoutTest.java)
- [WorkspaceSwitchTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceSwitchTest.java)
- [WorkspaceAccessGateTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceAccessGateTest.java)
