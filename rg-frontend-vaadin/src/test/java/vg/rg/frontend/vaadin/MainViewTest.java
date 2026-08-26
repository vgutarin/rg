package vg.rg.frontend.vaadin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vaadin.flow.spring.security.AuthenticationContext;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.unique.id.model.UniqueId;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;

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

    @Test
    void constructor_permittedPrincipal_createsOnlyPermittedNavigation() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User",
                Set.of(Permissions.Request.SUBMIT), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext);

        assertThat(view.navigationLabels()).containsExactly("nav.home");
    }

    @Test
    void constructor_permittedPrincipal_exposesPrincipalPermissions() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User",
                Set.of(Permissions.Location.VIEW, Permissions.Request.SUBMIT), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext);

        assertThat(view.visiblePermissions()).containsExactlyInAnyOrder(
                Permissions.Location.VIEW, Permissions.Request.SUBMIT);
    }

    @Test
    void constructor_authenticatedPrincipal_doesNotRenderIdentity() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", Set.of(Permissions.Location.VIEW), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext);

        assertThat(view.getElement().getText()).doesNotContain(principal.userUniqueId().toString());
    }

    @Test
    void selectLocale_englishSelection_updatesServerSessionLocale() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());
        var view = new MainView(localization, authenticationContext);

        view.selectLocale(Locale.ENGLISH);

        verify(localization).setCurrentLocale(Locale.ENGLISH);
    }

    @Test
    void constructor_defaultView_hasNoSecureFacadeDependency() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext);

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

        var view = new MainView(localization, authenticationContext);

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

        var view = new MainView(localization, authenticationContext);

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

        var view = new MainView(localization, authenticationContext);

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

        var view = new MainView(localization, authenticationContext);

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

        var view = new MainView(localization, authenticationContext);

        // Telegram Mini App sessions have no local login to return to, so the logout action is hidden.
        assertThat(view.sessionActionVisible()).isFalse();
    }

    @Test
    void constructor_unauthenticated_keepsLoginActionVisible() {
        when(localization.getProvidedLocales()).thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.empty());

        var view = new MainView(localization, authenticationContext);

        // Hiding is scoped to the Telegram flow; an unauthenticated visitor still sees the login action.
        assertThat(view.sessionActionVisible()).isTrue();
        assertThat(view.sessionActionText()).isEqualTo("Login");
    }

    @Test
    void constructor_nullSubject_keepsOnlyHomeNavigationAndSuppressesEffectivePermissions() {
        configureLocalization();
        var principal = new AuthenticatedUserPrincipal(
                null, "Sensitive Name", Set.of(Permissions.Request.SUBMIT, Permissions.Reports.VIEW),
                false, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext);

        // Home navigation is always present; a null subject exposes no effective permissions and no
        // permission-gated navigation beyond Home.
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
            case "nav.reports" -> "Звіти";
            default -> invocation.getArgument(0);
        });
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), null,
                Set.of(Permissions.Request.SUBMIT, Permissions.Reports.VIEW, "unknown:view"),
                true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));

        var view = new MainView(localization, authenticationContext);

        assertThat(view.navigationLabels()).containsExactly("Головна", "Звіти");
        assertThat(view.visiblePermissions()).containsExactlyInAnyOrder(
                Permissions.Request.SUBMIT, Permissions.Reports.VIEW);
    }

    private void configureLocalization() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
