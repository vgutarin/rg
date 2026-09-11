package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentNavigationProviderTest {

    private static final UniqueId WORKSPACE = new UniqueId(5001L);

    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceSelectionService selectionService;

    @Test
    void withoutWorkspaceOwnership_returnsHomeAndDoesNotResolveTheWorkspace() {
        var entries = provider().visibleEntries(Set.of("unknown:view"));

        assertThat(entries).extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home");
        verify(selectionService, never()).activeWorkspace();
        verify(authorityChecker, never()).hasAuthority(any(UniqueId.class), anyString());
    }

    @Test
    void experimentParticipant_seesDatesWithoutResolvingTheWorkspace() {
        var entries = provider().visibleEntries(Set.of(Permissions.Experiment.PARTICIPANT));

        assertThat(entries).extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home", "nav.dates");
        verify(selectionService, never()).activeWorkspace();
        verify(authorityChecker, never()).hasAuthority(any(UniqueId.class), anyString());
    }

    @Test
    void withoutAnActiveWorkspace_returnsUngatedEntriesAndDoesNotCheckContainedPermissions() {
        when(selectionService.activeWorkspace()).thenReturn(null);

        var entries = provider().visibleEntries(Set.of(Permissions.Workspace.OWNER));

        assertThat(entries).extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home");
        verify(selectionService).activeWorkspace();
        verify(authorityChecker, never()).hasAuthority(any(UniqueId.class), anyString());
    }

    @Test
    void locationsPermissionControlsOnlyTheLocationsEntry() {
        assertThat(entriesVisibleFor(LocalPermissions.Location.LIST))
                .extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home", "nav.locations");
    }

    @Test
    void participantsPermissionControlsOnlyTheParticipantsEntry() {
        assertThat(entriesVisibleFor(LocalPermissions.WorkspaceParticipant.LIST))
                .extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home", "nav.participants");
    }

    @Test
    void eventsPermissionControlsOnlyTheEventsEntry() {
        assertThat(entriesVisibleFor(LocalPermissions.WorkspaceEvent.LIST))
                .extracting(ContentNavigationProvider.Entry::messageKey)
                .containsExactly("nav.home", "nav.events");
    }

    @Test
    void returnsEveryKnownEntryInNavigationOrderWithItsPresentationMetadata() {
        activeWorkspace();
        when(authorityChecker.hasAuthority(eq(WORKSPACE), anyString())).thenReturn(true);

        var entries = provider().visibleEntries(Set.of(Permissions.Experiment.PARTICIPANT,
                Permissions.Workspace.OWNER));

        assertThat(entries)
                .extracting(
                        ContentNavigationProvider.Entry::messageKey,
                        ContentNavigationProvider.Entry::path,
                        ContentNavigationProvider.Entry::icon)
                .containsExactly(
                        tuple("nav.home", "/", VaadinIcon.HOME),
                        tuple("nav.dates", "/date-time-examples", VaadinIcon.CALENDAR),
                        tuple("nav.locations", "/workspaces/locations", VaadinIcon.MAP_MARKER),
                        tuple("nav.participants", "/workspaces/participants", VaadinIcon.USERS),
                        tuple("nav.events", "/workspaces/events", VaadinIcon.CALENDAR));
        verify(selectionService).activeWorkspace();
    }

    private List<ContentNavigationProvider.Entry> entriesVisibleFor(String grantedPermission) {
        activeWorkspace();
        when(authorityChecker.hasAuthority(eq(WORKSPACE), anyString()))
                .thenAnswer(invocation -> grantedPermission.equals(invocation.getArgument(1)));
        return provider().visibleEntries(Set.of(Permissions.Workspace.OWNER));
    }

    private void activeWorkspace() {
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).defaultWorkspace(true).build());
    }

    private ContentNavigationProvider provider() {
        return new ContentNavigationProvider(authorityChecker, selectionService);
    }
}
