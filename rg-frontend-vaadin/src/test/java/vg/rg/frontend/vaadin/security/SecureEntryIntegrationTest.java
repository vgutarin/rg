package vg.rg.frontend.vaadin.security;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.telegram.TelegramAuthView;
import vg.rg.frontend.vaadin.view.LandingView;
import vg.rg.frontend.vaadin.view.NoAccessView;
import vg.rg.security.AuthorizationApplicationService;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.unique.id.model.UniqueId;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.AuthorizationOutcome;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(SecureEntryIntegrationTest.TestConfiguration.class)
class SecureEntryIntegrationTest {

    @Autowired AuthorizationApplicationService authorizationService;
    @Autowired ApplicationSecurityContextService securityContextService;
    @Autowired LocalizationService localization;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.reset(authorizationService);
    }

    @Test
    void secureEntry_establishedPrincipal_reachesPermittedContent() {
        var principal = principal(new UniqueId(1234L), "Session Name", Set.of("location:view"));
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.authorized(principal));
        var ui = mock(UI.class);
        var view = view();

        try (var currentUi = mockStatic(UI.class)) {
            currentUi.when(UI::getCurrent).thenReturn(ui);
            view.authenticate("auth_date=1&hash=x");

            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("location:view");
            verify(ui).navigate(LandingView.class);
        }
    }

    @Test
    void secureEntry_nullSubject_reachesNoAccessWithoutRenderingIdentity() {
        var principal = principal(null, "Sensitive Session Name", Set.of("location:view"));
        when(authorizationService.redeem(any())).thenReturn(AuthorizationOutcome.authorized(principal));
        var ui = mock(UI.class);
        var view = view();

        try (var currentUi = mockStatic(UI.class)) {
            currentUi.when(UI::getCurrent).thenReturn(ui);
            view.authenticate("auth_date=1&hash=x");

            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(principal);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
            assertThat(renderedText(view)).doesNotContain("Sensitive Session Name", "location:view", "auth_date");
            verify(ui).navigate(NoAccessView.class);
            verify(ui, never()).navigate(LandingView.class);
        }
    }

    private TelegramAuthView view() {
        return new TelegramAuthView(authorizationService, securityContextService, localization);
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        AuthorizationApplicationService authorizationApplicationService() {
            return mock(AuthorizationApplicationService.class);
        }

        @Bean
        AuthenticationEventPublisher authenticationEventPublisher() {
            return mock(AuthenticationEventPublisher.class);
        }

        @Bean
        ApplicationSecurityContextService applicationSecurityContextService(
                AuthenticationEventPublisher events) {
            return new ApplicationSecurityContextService(events);
        }

        @Bean
        LocalizationService localizationService() {
            var messages = new ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return new LocalizationService(messages);
        }
    }

    private AuthenticatedUserPrincipal principal(UniqueId userUniqueId, String name, Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                userUniqueId, name, permissions, false, AuthenticationFlow.TELEGRAM);
    }

    private String renderedText(Component component) {
        var ownText = component instanceof HasText textComponent ? textComponent.getText() : "";
        return ownText + component.getChildren()
                .map(this::renderedText)
                .reduce("", String::concat);
    }
}
