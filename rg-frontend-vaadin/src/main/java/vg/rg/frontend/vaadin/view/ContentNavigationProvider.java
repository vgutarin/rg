package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.icon.VaadinIcon;
import lombok.RequiredArgsConstructor;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The application destinations shown at the first navigation level.
 *
 * <p>Both the drawer and the home launcher use these entries. Resolving the active workspace can
 * provision a default workspace, so this resolves it once before independently evaluating the
 * workspace-scoped entries.
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
class ContentNavigationProvider {

    private static final Entry HOME = new Entry("nav.home", "/", VaadinIcon.HOME);
    private static final Entry DATES = new Entry("nav.dates", "/date-time-examples", VaadinIcon.CALENDAR);
    private static final List<WorkspaceEntry> WORKSPACE_ENTRIES = List.of(
            new WorkspaceEntry(new Entry("nav.locations", "/workspaces/locations", VaadinIcon.MAP_MARKER),
                    LocalPermissions.Location.LIST),
            new WorkspaceEntry(new Entry("nav.participants", "/workspaces/participants", VaadinIcon.USERS),
                    LocalPermissions.WorkspaceParticipant.LIST),
            new WorkspaceEntry(new Entry("nav.events", "/workspaces/events", VaadinIcon.CALENDAR),
                    LocalPermissions.WorkspaceEvent.LIST));

    private final AuthorityChecker authorityChecker;
    private final WorkspaceSelectionService selectionService;

    public List<Entry> visibleEntries(Set<String> permissions) {
        var entries = new ArrayList<Entry>();
        entries.add(HOME);
        if (permissions.contains(Permissions.Experiment.PARTICIPANT)) {
            entries.add(DATES);
        }
        if (!permissions.contains(Permissions.Workspace.OWNER)) {
            return List.copyOf(entries);
        }

        var activeWorkspace = selectionService.activeWorkspace();
        if (activeWorkspace == null || activeWorkspace.getUniqueId() == null) {
            return List.copyOf(entries);
        }
        var workspaceId = activeWorkspace.getUniqueId();
        WORKSPACE_ENTRIES.stream()
                .filter(entry -> authorityChecker.hasAuthority(workspaceId, entry.listPermission()))
                .map(WorkspaceEntry::entry)
                .forEach(entries::add);
        return List.copyOf(entries);
    }

    private record WorkspaceEntry(Entry entry, String listPermission) { }

    public record Entry(String messageKey, String path, VaadinIcon icon) { }
}
