package vg.rg.frontend.vaadin.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.unique.id.model.UniqueId;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.service.ProtectedActionService;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionAwareViewsTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock AuthenticationContext authenticationContext;
    @Mock ProtectedActionService protectedActionService;
    @Mock BeforeEnterEvent event;

    @Test
    void annotations_permissionProtectedViews_usePermissionSemantics() {
        assertThat(LandingView.class).hasAnnotation(PermitAll.class);
        assertThat(ReportsView.class).hasAnnotation(PermitAll.class);
        assertThat(LandingView.class.isAnnotationPresent(RolesAllowed.class)).isFalse();
        assertThat(ReportsView.class.isAnnotationPresent(RolesAllowed.class)).isFalse();
    }

    @Test
    void beforeEnter_missingPrincipal_reroutesToAccessDenied() {
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.empty());
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);

        landing.beforeEnter(event);

        verify(event).rerouteTo(AccessDeniedErrorView.class);
    }

    @Test
    void beforeEnter_presentPrincipal_rendersHome() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Location.VIEW))));
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);

        landing.beforeEnter(event);

        assertThat(descendants(landing)).anyMatch(com.vaadin.flow.component.html.H1.class::isInstance);
        verify(event, never()).rerouteTo(AccessDeniedErrorView.class);
    }

    @Test
    void requiredPermission_reportsView_returnsReportsViewPermission() {
        var reports = new ReportsView(localization, authorityChecker, authenticationContext);

        assertThat(reports.requiredPermission()).isEqualTo(Permissions.Reports.VIEW);
    }

    @Test
    void beforeEnter_emptyPermissionSet_reroutesToNoAccess() {
        when(authorityChecker.hasAuthority(Permissions.Reports.VIEW)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of())));
        var reports = new ReportsView(localization, authorityChecker, authenticationContext);

        reports.beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    @Test
    void localeChange_presentPrincipal_reRendersContent() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Location.VIEW))));
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);

        landing.localeChange(null);

        assertThat(descendants(landing)).anyMatch(com.vaadin.flow.component.html.H1.class::isInstance);
    }

    @Test
    void beforeEnter_nullSubjectPrincipal_rendersHomeWithoutReroute() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(null, Set.of())));
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);

        landing.beforeEnter(event);

        assertThat(descendants(landing)).anyMatch(com.vaadin.flow.component.html.H1.class::isInstance);
        verify(event, never()).rerouteTo(AccessDeniedErrorView.class);
        verify(event, never()).rerouteTo(NoAccessView.class);
    }

    @Test
    void beforeEnter_recognizedAndUnknownPermissions_rendersOnlyRecognizedCapabilities() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Request.SUBMIT)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Location.VIEW, "unknown:view"))));
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);

        landing.beforeEnter(event);

        verify(localization).i18n("permission." + Permissions.Location.VIEW);
        verify(localization, never()).i18n("permission.unknown:view");
    }

    @Test
    void beforeEnter_reportsPermission_rendersReportsWhileDirectMissingPermissionDenies() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Reports.VIEW)).thenReturn(true);
        var reports = new ReportsView(localization, authorityChecker, authenticationContext);

        reports.beforeEnter(event);

        assertThat(descendants(reports)).anyMatch(com.vaadin.flow.component.html.H1.class::isInstance);
    }

    private AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return principal(new UniqueId(1234L), permissions);
    }

    private AuthenticatedUserPrincipal principal(UniqueId userUniqueId, Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                userUniqueId, "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }

    private java.util.List<com.vaadin.flow.component.Component> descendants(
            com.vaadin.flow.component.Component component) {
        return component.getChildren()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child), descendants(child).stream()))
                .toList();
    }
}
