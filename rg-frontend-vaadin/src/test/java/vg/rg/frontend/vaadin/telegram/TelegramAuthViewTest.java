package vg.rg.frontend.vaadin.telegram;

import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import vg.rg.frontend.vaadin.security.ApplicationSecurityContextService;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.AuthorizationUiState;
import vg.rg.frontend.vaadin.view.LandingView;
import vg.rg.frontend.vaadin.view.NoAccessView;
import vg.rg.security.AuthorizationApplicationService;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.AuthenticationFlow;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramAuthViewTest {

    @Test
    void javaScript_defaultView_loadsOfficialTelegramSdk() {
        assertThat(TelegramAuthView.class.getAnnotationsByType(JavaScript.class))
                .extracting(JavaScript::value)
                .containsExactly("https://telegram.org/js/telegram-web-app.js?63");
    }

    @Mock AuthorizationApplicationService authorizationService;
    @Mock ApplicationSecurityContextService securityContextService;

    @Test
    void constructor_defaultView_displaysLocalizedLoadingState() {
        var view = view();

        assertThat(view.headingText()).isEqualTo("Перевіряємо безпечний доступ");
    }

    @Test
    void authenticate_emptyInitData_displaysLocalizedDeniedState() {
        var view = view();

        view.authenticate("");

        assertThat(view.headingText()).isEqualTo("Доступ не надано");
        assertThat(view.retryVisible()).isFalse();
    }

    @Test
    void authenticate_unavailableOutcome_offersSafeExplicitRetry() {
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.unavailable(java.time.Duration.ofSeconds(5)));
        var view = view();

        view.authenticate("auth_date=1&hash=x");

        assertThat(view.headingText()).isEqualTo("Сервіс тимчасово недоступний");
        assertThat(view.retryVisible()).isTrue();
        assertThat(view.authorizationState()).isEqualTo(AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void retry_unavailableAttempt_startsExactlyOneExplicitInFlightRequest() {
        when(authorizationService.redeem(any()))
                .thenReturn(AuthorizationOutcome.unavailable(java.time.Duration.ofSeconds(5)))
                .thenReturn(AuthorizationOutcome.denied());
        var view = view();
        view.authenticate("auth_date=1&hash=x");

        view.activateRetry();
        view.activateRetry();

        assertThat(view.authorizationState()).isEqualTo(AuthorizationUiState.RETRYING);
        assertThat(view.retryVisible()).isTrue();
        assertThat(view.retryEnabled()).isFalse();
        verify(authorizationService).redeem(any());

        view.authenticate("auth_date=1&hash=x");

        verify(authorizationService, times(2)).redeem(any());
        assertThat(view.authorizationState()).isEqualTo(AuthorizationUiState.DENIED);
    }

    @Test
    void authenticate_authorizedOutcome_installsPrincipal() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1234", "Test User", Set.of("home:view"), true,
                AuthenticationFlow.TELEGRAM);
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.authorized(principal));
        var view = view();

        view.authenticate("auth_date=1&hash=x");

        verify(securityContextService).authenticate(principal);
    }

    @Test
    void authenticate_boundedCallback_forwardsOpaqueValueUnchanged() {
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.denied());
        var view = view();

        view.authenticate("auth_date=1&query_id=opaque&hash=x");

        verify(authorizationService).redeem(org.mockito.ArgumentMatchers.argThat(request ->
                request.initData().equals("auth_date=1&query_id=opaque&hash=x")));
    }

    @Test
    void authenticate_establishedPrincipal_navigatesToLanding() {
        var principal = principal("subject-1234");
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.authorized(principal));
        var ui = mock(UI.class);
        var view = view();
        try (var currentUi = mockStatic(UI.class)) {
            currentUi.when(UI::getCurrent).thenReturn(ui);

            view.authenticate("auth_date=1&hash=x");

            verify(ui).navigate(LandingView.class);
        }
    }

    @Test
    void authenticate_nullSubject_installsSessionAndNavigatesToNoAccess() {
        var principal = principal(null);
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.authorized(principal));
        var ui = mock(UI.class);
        var view = view();
        try (var currentUi = mockStatic(UI.class)) {
            currentUi.when(UI::getCurrent).thenReturn(ui);

            view.authenticate("auth_date=1&hash=x");

            verify(securityContextService).authenticate(principal);
            verify(ui).navigate(NoAccessView.class);
            verify(ui, never()).navigate(LandingView.class);
        }
    }

    @Test
    void authenticate_closedOutcomes_doNotInstallPrincipalOrExposeRetry() {
        for (var outcome : java.util.List.of(
                AuthorizationOutcome.invalidRequest(), AuthorizationOutcome.expired(),
                AuthorizationOutcome.denied(), AuthorizationOutcome.incompatible())) {
            org.mockito.Mockito.reset(authorizationService, securityContextService);
            when(authorizationService.redeem(any())).thenReturn(outcome);
            var view = view();

            view.authenticate("auth_date=1&hash=x");

            assertThat(view.retryVisible()).isFalse();
            verify(securityContextService, never()).authenticate(any());
            verify(securityContextService).clear();
            assertThat(renderedText(view)).doesNotContain("subject", "Test User", "auth_date", "hash=x");
        }
    }

    @Test
    void authenticate_duplicateCallback_redeemsOnlyOnce() {
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.denied());
        var view = view();

        view.authenticate("auth_date=1&hash=x");
        view.authenticate("auth_date=1&hash=x");

        verify(authorizationService).redeem(any());
    }

    @Test
    void authenticate_incompatibleOutcome_displaysLocalizedSafeGuidance() {
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.incompatible());
        var view = view();

        view.authenticate("auth_date=1&hash=x");

        assertThat(view.headingText()).isEqualTo("Потрібне оновлення");
        assertThat(view.retryVisible()).isFalse();
        assertThat(view.authorizationState()).isEqualTo(AuthorizationUiState.INCOMPATIBLE);
        assertThat(renderedText(view)).doesNotContain("auth_date", "hash=x");
    }

    private AuthenticatedUserPrincipal principal(String subject) {
        return new AuthenticatedUserPrincipal(
                subject, "Test User", Set.of("home:view"), true, AuthenticationFlow.TELEGRAM);
    }

    private String renderedText(Component component) {
        var ownText = component instanceof HasText textComponent ? textComponent.getText() : "";
        return ownText + component.getChildren()
                .map(this::renderedText)
                .reduce("", String::concat);
    }

    private TelegramAuthView view() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new TelegramAuthView(authorizationService, securityContextService, new LocalizationService(messages));
    }
}
