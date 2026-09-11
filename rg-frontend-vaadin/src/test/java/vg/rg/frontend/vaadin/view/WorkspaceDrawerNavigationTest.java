package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Navigation visibility for the workspace layer.
 *
 * <p>Locations and participants sit at the <strong>top level</strong>: the workspace is the scope they
 * live in, not a place the user has to navigate through. Each entry is shown only when the caller may
 * actually list that type in the workspace they are working in, which is a check against that workspace
 * rather than against a capability the principal carries around.
 *
 * <p>The workspace section's own entry is <strong>withheld for everyone</strong> for now. Its routes,
 * layout and gate all still work — only the way in from the drawer is absent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceDrawerNavigationTest {

    private static final UniqueId WORKSPACE = new UniqueId(5001L);

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceSelectionService selectionService;

    @Test
    void experimentParticipantSeesDateTimeExamplesWithoutWorkspaceProvisioning() {
        var view = mainViewFor(Set.of(Permissions.Experiment.PARTICIPANT), false);
        assertThat(view.navigationLabels()).contains("nav.dates");
        assertThat(view.navigationPaths()).contains("/date-time-examples");
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void withoutExperimentParticipant_dateTimeExamplesAreHiddenWithoutWorkspaceProvisioning() {
        var view = mainViewFor(Set.of(), false);

        assertThat(view.navigationLabels()).doesNotContain("nav.dates");
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void permittedInTheActiveWorkspace_seesTheTopLevelLocationsEntry() {
        var view = mainViewFor(Set.of(Permissions.Workspace.OWNER), true);

        assertThat(view.navigationLabels()).contains("nav.locations");
    }

    @Test
    void theEntryIsScopedToTheActiveWorkspace_notToAHeldCapability() {
        // The check passes the workspace being worked in. Denying it hides the entry even though the
        // principal holds the app-wide gate -- which is the whole point of scoping it.
        var view = mainViewFor(Set.of(Permissions.Workspace.OWNER), false);

        assertThat(view.navigationLabels()).doesNotContain("nav.locations");
    }

    @Test
    void withoutTheWorkspacePermission_thereIsNoLocationsEntryAndNothingIsProvisioned() {
        var view = mainViewFor(Set.of("unknown:view"), true);

        assertThat(view.navigationLabels()).doesNotContain("nav.locations");
        // Resolving the active workspace is what creates a default, so a user with no workspace
        // permission must not reach it -- and could not, since that call is gated on the same permission.
        verify(selectionService, never()).activeWorkspace();
        verify(authorityChecker, never()).hasAuthority(any(UniqueId.class), anyString());
    }

    @Test
    void holdingOnlyLocationCapabilities_revealsNothing() {
        // Local capabilities are not a gate. Without workspace:owner there is no workspace to scope to.
        var view = mainViewFor(LocalPermissions.Location.ALL, true);

        assertThat(view.navigationLabels()).doesNotContain("nav.locations");
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void permittedInTheActiveWorkspace_seesTheTopLevelParticipantsEntry() {
        var view = mainViewFor(Set.of(Permissions.Workspace.OWNER), true, true);

        assertThat(view.navigationLabels()).contains("nav.participants");
        assertThat(view.navigationPaths()).contains("/workspaces/participants");
    }

    @Test
    void permittedInTheActiveWorkspace_seesTheTopLevelEventsEntry() {
        when(localization.getProvidedLocales())
                .thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(new UniqueId(1234L), "Test User",
                Set.of(Permissions.Workspace.OWNER), true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).defaultWorkspace(true).build());
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.WorkspaceEvent.LIST)).thenReturn(true);

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.navigationLabels()).contains("nav.events");
        assertThat(view.navigationPaths()).contains("/workspaces/events");
    }

    @Test
    void drawerRendersEveryDestinationAsACompleteNavigationTile() {
        var view = mainViewFor(Set.of(Permissions.Experiment.PARTICIPANT), false);

        assertThat(view.navigationTiles()).allSatisfy(tile -> {
            assertThat(tile.getClassNames()).contains("navigation-tile", "aura-surface");
            assertThat(tile.getElement().getAttribute("aria-label")).isNotBlank();
            assertThat(tile.getChildren().filter(Span.class::isInstance)).hasSize(1);
        });
    }

    /**
     * The two entries are independently gated. One scoped check denying must not take the other entry
     * with it — which a single combined condition would do, and which is easy to introduce when both
     * checks live in one method.
     */
    @Test
    void theTwoWorkspaceScopedEntriesAreGatedIndependently() {
        var locationsOnly = mainViewFor(Set.of(Permissions.Workspace.OWNER), true, false);
        assertThat(locationsOnly.navigationLabels())
                .contains("nav.locations")
                .doesNotContain("nav.participants");

        var participantsOnly = mainViewFor(Set.of(Permissions.Workspace.OWNER), false, true);
        assertThat(participantsOnly.navigationLabels())
                .contains("nav.participants")
                .doesNotContain("nav.locations");
    }

    @Test
    void withoutTheWorkspacePermission_thereIsNoParticipantsEntryEither() {
        var view = mainViewFor(Set.of("unknown:view"), true, true);

        assertThat(view.navigationLabels()).doesNotContain("nav.participants");
        verify(selectionService, never()).activeWorkspace();
    }

    /**
     * Resolving the active workspace <em>provisions a default</em> as a side effect, so it must happen
     * once per page load however many workspace-scoped entries the drawer grows.
     */
    @Test
    void theActiveWorkspaceIsResolvedOncePerRender() {
        mainViewFor(Set.of(Permissions.Workspace.OWNER), true, true);

        verify(selectionService, org.mockito.Mockito.times(1)).activeWorkspace();
    }

    @Test
    void theWorkspaceSectionEntryIsWithheldFromEveryone() {
        // Including from a holder of every app-wide permission there is.
        var owner = mainViewFor(Permissions.APP_WIDE, true, true);
        var stranger = mainViewFor(Set.of(), true, true);

        assertThat(owner.navigationLabels()).doesNotContain("nav.workspaces");
        assertThat(stranger.navigationLabels()).doesNotContain("nav.workspaces");
    }

    @Test
    void localPermissionsSurviveSanitisationForDisplayDecisions() {
        // Permissions.recognized drops purely local permissions, so a view must not depend on them being
        // present. This pins that the locations entry is driven by the scoped check, not by the set.
        var view = mainViewFor(
                Set.of(Permissions.Workspace.OWNER, LocalPermissions.Workspace.UPDATE), true);

        assertThat(view.visiblePermissions()).containsExactly(Permissions.Workspace.OWNER);
        assertThat(view.navigationLabels()).contains("nav.locations");
    }

    private MainView mainViewFor(Set<String> permissions, boolean mayListLocations) {
        return mainViewFor(permissions, mayListLocations, false);
    }

    private MainView mainViewFor(
            Set<String> permissions, boolean mayListLocations, boolean mayListParticipants) {
        when(localization.getProvidedLocales())
                .thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).defaultWorkspace(true).build());
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.LIST))
                .thenReturn(mayListLocations);
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.WorkspaceParticipant.LIST))
                .thenReturn(mayListParticipants);

        return new MainView(localization, authenticationContext, authorityChecker, selectionService);
    }
}
