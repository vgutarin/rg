package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.config.MapsClientProperties;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.service.MapsResolutionBridge;
import vg.rg.frontend.vaadin.view.auth.AccessDeniedErrorView;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.rg.service.workspace.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every route into the workspace section refuses an unentitled caller on its own, not only through the
 * section layout. Hiding a navigation entry is presentation; these {@code beforeEnter} checks are the
 * enforcement, so typing a URL is denied exactly as navigating is.
 *
 * <p>Each also asserts that being denied <em>provisions nothing</em>: a user who may not have a
 * workspace must not end up with one as a side effect of being turned away.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceAccessGateTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock AuthenticationContext authenticationContext;
    @Mock WorkspaceService workspaceService;
    @Mock WorkspaceSelectionService selectionService;
    @Mock WorkspaceLocationService locationService;
    @Mock MapsResolutionBridge mapsResolutionBridge;
    @Mock MapsClientProperties mapsClientProperties;
    @Mock BeforeEnterEvent event;

    @Test
    void annotations_workspaceViews_usePermissionSemantics() {
        // Permission-based, like the rest of the application: never role-based.
        for (var view : java.util.List.of(
                WorkspaceLayout.class, WorkspacesView.class, WorkspaceLocationsView.class)) {
            assertThat(view).hasAnnotation(PermitAll.class);
            assertThat(view.isAnnotationPresent(RolesAllowed.class)).isFalse();
        }
    }

    @Test
    void routes_liveUnderTheWorkspaceSection() {
        assertThat(WorkspacesView.class.getAnnotation(Route.class).value()).isEqualTo("workspaces");
        assertThat(WorkspaceLocationsView.class.getAnnotation(Route.class).value())
                .isEqualTo("workspaces/locations");
        // Both are children of the section layout, which is what confines the selector to it.
        assertThat(WorkspacesView.class.getAnnotation(Route.class).layout())
                .isEqualTo(WorkspaceLayout.class);
        assertThat(WorkspaceLocationsView.class.getAnnotation(Route.class).layout())
                .isEqualTo(WorkspaceLayout.class);
    }

    @Test
    void workspacesView_withoutTheGate_isReroutedAndProvisionsNothing() {
        denyGate();

        workspacesView().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
        verify(workspaceService, never()).ensureDefault();
        verify(workspaceService, never()).listOwned();
    }

    @Test
    void workspaceLocationsView_withoutTheGate_isReroutedAndProvisionsNothing() {
        denyGate();
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of())));

        workspaceLocationsView().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void workspaceLayout_withoutTheGate_isReroutedAndProvisionsNothing() {
        denyGate();

        workspaceLayout().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void localCapabilitiesAlone_openNoRoute() {
        // The gate is the app-wide permission. Holding every local capability opens nothing.
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        LocalPermissions.ALL.forEach(permission ->
                when(authorityChecker.hasAuthority(permission)).thenReturn(true));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(LocalPermissions.ALL)));

        workspacesView().beforeEnter(event);
        workspaceLayout().beforeEnter(event);

        verify(event, org.mockito.Mockito.times(2)).rerouteTo(NoAccessView.class);
        verify(selectionService, never()).activeWorkspace();
    }

    @Test
    void locationsView_withOtherPermissions_reroutesToAccessDeniedRatherThanNoAccess() {
        // A user with some capability elsewhere gets "denied", not "you have nothing" -- neither reveals
        // whether the workspace section or its contents exist.
        denyGate();
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Reports.READ))));

        workspaceLocationsView().beforeEnter(event);

        verify(event).rerouteTo(AccessDeniedErrorView.class);
        verify(selectionService, never()).activeWorkspace();
    }

    private void denyGate() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
    }

    private WorkspacesView workspacesView() {
        return new WorkspacesView(localization, authorityChecker, workspaceService);
    }

    private WorkspaceLayout workspaceLayout() {
        return new WorkspaceLayout(localization, authorityChecker, selectionService, workspaceService);
    }

    private WorkspaceLocationsView workspaceLocationsView() {
        return new WorkspaceLocationsView(localization, authorityChecker, authenticationContext,
                locationService, selectionService, mapsResolutionBridge, mapsClientProperties);
    }

    private static AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }
}
