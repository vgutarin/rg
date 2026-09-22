package vg.rg.frontend.vaadin.view.auth;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.frontend.vaadin.service.ApplicationSecurityContextService;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.home.JoinSocietyIntent;
import vg.rg.frontend.vaadin.view.home.LandingView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.service.security.AuthorizationApplicationService;
import vg.unique.id.model.UniqueId;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramAuthConsentTest {

    @Mock AuthorizationApplicationService authorization;
    @Mock ApplicationSecurityContextService securityContext;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingJoin_forwardsConsentToAuthorization() {
        when(authorization.redeem(any())).thenReturn(AuthorizationOutcome.denied());
        try (var intent = mockStatic(JoinSocietyIntent.class)) {
            intent.when(JoinSocietyIntent::isPending).thenReturn(true);

            view().authenticate("auth_date=1&hash=x");

            verify(authorization).redeem(argThat(request -> request.consentToKeepPersonalData()));
        }
    }

    @Test
    void normalLogin_doesNotGrantConsent() {
        when(authorization.redeem(any())).thenReturn(AuthorizationOutcome.denied());
        try (var intent = mockStatic(JoinSocietyIntent.class)) {
            view().authenticate("auth_date=1&hash=x");

            verify(authorization).redeem(argThat(request -> !request.consentToKeepPersonalData()));
        }
    }

    @Test
    void pendingJoin_reauthorizesAnAuthenticatedProvisionalViewer() {
        checkAttach(null, true, true);
    }

    @Test
    void provisionalViewerWithoutIntent_navigatesHomeWithoutReauthorization() {
        checkAttach(null, false, false);
    }

    @Test
    void establishedViewerWithIntent_navigatesHomeToResumeIt() {
        checkAttach(new UniqueId(123L), true, false);
    }

    @Test
    void unavailableConsentUpgrade_retainsIntentAndForwardsConsentOnExplicitRetry() {
        when(authorization.redeem(any())).thenReturn(AuthorizationOutcome.unavailable(Duration.ofSeconds(5)),
                AuthorizationOutcome.denied());
        try (var intent = mockStatic(JoinSocietyIntent.class)) {
            intent.when(JoinSocietyIntent::isPending).thenReturn(true);
            var view = view();
            view.authenticate("auth_date=1&hash=x");
            assertThat(view.retryEnabled()).isTrue();

            view.activateRetry();
            view.authenticate("auth_date=1&hash=x");

            verify(authorization, times(2)).redeem(argThat(request -> request.consentToKeepPersonalData()));
            intent.verify(JoinSocietyIntent::consume, never());
        }
    }

    private void checkAttach(UniqueId subject, boolean pending, boolean shouldReauthorize) {
        var principal = AuthenticatedUserPrincipal.builder().userUniqueId(subject).permissions(Set.of())
                .authenticationFlow(AuthenticationFlow.TELEGRAM).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        var ui = mock(UI.class);
        var view = spy(view());
        if (shouldReauthorize) doNothing().when(view).requestTelegramInitData();
        try (var intent = mockStatic(JoinSocietyIntent.class); var currentUi = mockStatic(UI.class)) {
            intent.when(JoinSocietyIntent::isPending).thenReturn(pending);
            currentUi.when(UI::getCurrent).thenReturn(ui);

            view.onAttach(new AttachEvent(view, true));

            if (shouldReauthorize) {
                verify(view).requestTelegramInitData();
                verifyNoInteractions(ui);
            } else {
                verify(view, never()).requestTelegramInitData();
                verify(ui).navigate(LandingView.class);
            }
        }
    }

    private TelegramAuthView view() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new TelegramAuthView(authorization, securityContext, new LocalizationService(messages));
    }
}
