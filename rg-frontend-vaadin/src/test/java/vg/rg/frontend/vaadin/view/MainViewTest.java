package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainViewTest {

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceSelectionService selectionService;

    /**
     * The header shows the <strong>leaf</strong> view's title, not the enclosing layout's.
     *
     * <p>{@code getContent()} is not the view for a nested route: everything under
     * {@code WorkspaceLayout} makes that layout the content, and it declares no {@code @PageTitle}, so
     * the locations and participants screens both showed the project name. Nothing failed — a wrong
     * title is silent — which is why this test exists.
     */
    @Test
    void afterNavigation_nestedRoute_showsTheLeafViewsTitleRatherThanTheProjectName() {
        var view = mainView();

        view.afterNavigation(navigationTo(new EnclosingLayout(), new LeafView()));

        assertThat(view.currentTitleText()).isEqualTo("page.leaf.title");
    }

    /** Order-independent: only the leaf declares a title, so either end of the chain may come first. */
    @Test
    void afterNavigation_findsTheTitleWhicheverEndOfTheChainTheLeafIsAt() {
        var view = mainView();

        view.afterNavigation(navigationTo(new LeafView(), new EnclosingLayout()));

        assertThat(view.currentTitleText()).isEqualTo("page.leaf.title");
    }

    @Test
    void afterNavigation_nothingInTheChainDeclaresATitle_fallsBackToTheProjectName() {
        var view = mainView();

        view.afterNavigation(navigationTo(new EnclosingLayout()));

        assertThat(view.currentTitleText()).isEqualTo("project.name");
    }

    /** A locale change has no navigation event to consult, so the resolved key has to be remembered. */
    @Test
    void localeChange_retranslatesTheCurrentViewsTitle() {
        var view = mainView();
        view.afterNavigation(navigationTo(new LeafView()));

        when(localization.i18n("page.leaf.title")).thenReturn("Translated afresh");
        view.localeChange(new com.vaadin.flow.i18n.LocaleChangeEvent(new com.vaadin.flow.component.UI(),
                Locale.ENGLISH));

        assertThat(view.currentTitleText()).isEqualTo("Translated afresh");
    }

    @com.vaadin.flow.router.PageTitle("page.leaf.title")
    private static class LeafView extends com.vaadin.flow.component.html.Div {
    }

    /** Stands in for {@code WorkspaceLayout}: a real router layout with no title of its own. */
    private static class EnclosingLayout extends com.vaadin.flow.component.html.Div {
    }

    private static com.vaadin.flow.router.AfterNavigationEvent navigationTo(
            com.vaadin.flow.component.Component... chain) {
        // Mocked rather than constructed: a real AfterNavigationEvent needs a Router and a
        // LocationChangeEvent, and what is under test is how MainView reads the chain, not routing.
        var event = org.mockito.Mockito.mock(com.vaadin.flow.router.AfterNavigationEvent.class);
        org.mockito.Mockito.when(event.getActiveChain())
                .thenReturn(List.of((com.vaadin.flow.component.HasElement[]) chain));
        return event;
    }

    private MainView mainView() {
        when(localization.getProvidedLocales())
                .thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", Set.of(Permissions.Workspace.OWNER), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));
        return new MainView(localization, authenticationContext, authorityChecker, selectionService);
    }

    @Test
    void constructor_permittedPrincipal_createsOnlyPermittedNavigation() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User",
                Set.of(Permissions.Workspace.OWNER), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.navigationLabels()).containsExactly("nav.home");
    }

    @Test
    void constructor_permittedPrincipal_exposesPrincipalPermissions() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User",
                Set.of(Permissions.Workspace.OWNER), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.visiblePermissions()).containsExactly(Permissions.Workspace.OWNER);
    }

    @Test
    void constructor_authenticatedPrincipal_doesNotRenderIdentity() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", Set.of(Permissions.Workspace.OWNER), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.getElement().getText()).doesNotContain(principal.userUniqueId().toString());
    }

    @Test
    void selectLocale_englishSelection_updatesServerSessionLocale() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());
        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        view.selectLocale(Locale.ENGLISH);

        verify(localization).setCurrentLocale(Locale.ENGLISH);
    }

    @Test
    void constructor_defaultView_hasNoSecureFacadeDependency() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.getClass().getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains("SecureAuthorizationFacade"));
    }

    @Test
    void constructor_ukrainianSession_selectsUkrainianLocale() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "locale.uk-UA" -> "Українська";
            case "locale.en" -> "English";
            default -> invocation.getArgument(0);
        });
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.selectedLocale()).isEqualTo(LocalizationService.DEFAULT_LOCALE);
    }

    @Test
    void localeLabel_ukrainianLocale_displaysUkrainianName() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "locale.uk-UA" -> "Українська";
            case "locale.en" -> "English";
            default -> invocation.getArgument(0);
        });
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.localeLabel(LocalizationService.DEFAULT_LOCALE)).isEqualTo("Українська");
    }

    @Test
    void localeLabel_englishLocale_displaysEnglishName() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "locale.uk-UA" -> "Українська";
            case "locale.en" -> "English";
            default -> invocation.getArgument(0);
        });
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.localeLabel(Locale.ENGLISH)).isEqualTo("English");
    }

    @Test
    void constructor_authenticatedPrincipalWithoutPermissions_exposesNoPermissions() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", Set.of(), true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.visiblePermissions()).isEmpty();
    }

    @Test
    void constructor_telegramFlow_hidesLogoutAction() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", Set.of(), true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        // Telegram Mini App sessions have no local login to return to, so the logout action is hidden.
        assertThat(view.sessionActionVisible()).isFalse();
    }

    @Test
    void constructor_unauthenticated_keepsLoginActionVisible() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        // Hiding is scoped to the Telegram flow; an unauthenticated visitor still sees the login action.
        assertThat(view.sessionActionVisible()).isTrue();
        assertThat(view.sessionActionText()).isEqualTo("Login");
    }

    @Test
    void constructor_nullSubject_keepsUngatedNavigationAndSuppressesEffectivePermissions() {
        configureLocalization();
        var principal = new AuthenticatedUserPrincipal(
                null, "Sensitive Name", Set.of(Permissions.Workspace.OWNER),
                false, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        // Home is always present; a null subject exposes no effective permissions
        // and no permission-gated navigation.
        assertThat(view.navigationLabels()).containsExactly("nav.home");
        assertThat(view.visiblePermissions()).isEmpty();
        assertThat(view.getElement().getText()).doesNotContain("Sensitive Name");
    }

    @Test
    void constructor_unknownPermissions_areInertWhileRecognizedNavigationRemainsLocalized() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "nav.home" -> "Головна";
            case "nav.dates" -> "Дати й час";
            default -> invocation.getArgument(0);
        });
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), null,
                Set.of(Permissions.Experiment.PARTICIPANT, "unknown:view"),
                true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext, authorityChecker, selectionService);

        assertThat(view.navigationLabels()).containsExactly("Головна", "Дати й час");
        assertThat(view.visiblePermissions()).containsExactly(Permissions.Experiment.PARTICIPANT);
    }

    private void configureLocalization() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
