package vg.rg.frontend.vaadin.view.home;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandingViewTest {

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock BeforeEnterEvent event;

    @Test
    void authenticatedUserSeesTheWelcomeMessage() {
        when(localization.i18n("home.welcome")).thenReturn("Welcome");
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal()));

        var landing = landing();
        landing.beforeEnter(event);

        assertThat(landing.welcomeText()).isEqualTo("Welcome");
    }

    @Test
    void localeChangeRetranslatesTheWelcomeMessage() {
        when(localization.i18n(anyString())).thenReturn("First welcome");
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal()));

        var landing = landing();
        landing.beforeEnter(event);

        when(localization.i18n(anyString())).thenReturn("Second welcome");
        landing.localeChange(null);

        assertThat(landing.welcomeText()).isEqualTo("Second welcome");
    }

    private LandingView landing() {
        return new LandingView(localization, authenticationContext);
    }

    private AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal(new UniqueId(1234L), "Test User", Set.of(), true,
                AuthenticationFlow.TELEGRAM);
    }
}
