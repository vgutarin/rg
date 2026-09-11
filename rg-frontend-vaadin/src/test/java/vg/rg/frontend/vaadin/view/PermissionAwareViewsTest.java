package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.AccessDeniedErrorView;
import vg.rg.frontend.vaadin.view.home.LandingView;
import vg.rg.frontend.vaadin.view.workspace.WorkspaceLayout;
import vg.rg.frontend.vaadin.view.workspace.WorkspaceLocationsView;
import vg.rg.frontend.vaadin.view.workspace.WorkspacesView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionAwareViewsTest {

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock BeforeEnterEvent event;

    @Test
    void viewsUsePermissionSemanticsRatherThanRoles() {
        for (var view : List.of(LandingView.class, WorkspaceLayout.class,
                WorkspacesView.class, WorkspaceLocationsView.class)) {
            assertThat(view).hasAnnotation(PermitAll.class);
            assertThat(view.isAnnotationPresent(RolesAllowed.class)).isFalse();
        }
    }

    @Test
    void landingWithoutAPrincipalReroutesToAccessDenied() {
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.empty());

        landing().beforeEnter(event);

        verify(event).rerouteTo(AccessDeniedErrorView.class);
    }

    @Test
    void landingWithNoWorkspaceOwnershipRemainsAccessibleWithWelcomeMessage() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(null, Set.of())));
        var landing = landing();

        landing.beforeEnter(event);

        assertThat(descendants(landing)).anyMatch(H1.class::isInstance);
        verify(event, never()).rerouteTo(AccessDeniedErrorView.class);
    }

    private LandingView landing() {
        return new LandingView(localization, authenticationContext);
    }

    private AuthenticatedUserPrincipal principal(UniqueId userUniqueId, Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                userUniqueId, "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }

    private List<com.vaadin.flow.component.Component> descendants(com.vaadin.flow.component.Component component) {
        return component.getChildren()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child), descendants(child).stream()))
                .toList();
    }
}
