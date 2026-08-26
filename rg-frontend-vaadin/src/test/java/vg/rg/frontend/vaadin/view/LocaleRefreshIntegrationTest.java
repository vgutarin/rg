package vg.rg.frontend.vaadin;

import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocaleRefreshIntegrationTest {

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;

    @Test
    void localeChange_existingShell_preservesNavigation() {
        var view = authenticatedView();
        var originalNavigation = view.navigationLabels();

        view.selectLocale(Locale.ENGLISH);
        view.localeChange(mock(LocaleChangeEvent.class));

        assertThat(view.navigationLabels()).isEqualTo(originalNavigation);
    }

    @Test
    void localeChange_existingShell_preservesPermissions() {
        var view = authenticatedView();
        var originalPermissions = view.visiblePermissions();

        view.selectLocale(Locale.ENGLISH);
        view.localeChange(mock(LocaleChangeEvent.class));

        assertThat(view.visiblePermissions()).isEqualTo(originalPermissions);
    }

    @Test
    void setCurrentLocale_activeVaadinSession_updatesSessionLocale() {
        var messageSource = mock(org.springframework.context.MessageSource.class);
        var service = new LocalizationService(messageSource);
        var session = mock(VaadinSession.class);
        var ui = mock(UI.class);
        when(ui.getLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        try (var currentSession = mockStatic(VaadinSession.class);
             var currentUi = mockStatic(UI.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(session);
            currentUi.when(UI::getCurrent).thenReturn(ui);

            service.setCurrentLocale(Locale.ENGLISH);

            verify(session).setLocale(Locale.ENGLISH);
        }
    }

    @Test
    void setCurrentLocale_activeUi_updatesUiLocale() {
        var messageSource = mock(org.springframework.context.MessageSource.class);
        var service = new LocalizationService(messageSource);
        var session = mock(VaadinSession.class);
        var ui = mock(UI.class);
        when(ui.getLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        try (var currentSession = mockStatic(VaadinSession.class);
             var currentUi = mockStatic(UI.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(session);
            currentUi.when(UI::getCurrent).thenReturn(ui);

            service.setCurrentLocale(Locale.ENGLISH);

            verify(ui).setLocale(Locale.ENGLISH);
        }
    }

    private MainView authenticatedView() {
        when(localization.getProvidedLocales()).thenReturn(
                List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User",
                Set.of(Permissions.Request.SUBMIT, Permissions.Reports.VIEW), true,
                AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));
        return new MainView(localization, authenticationContext);
    }
}
