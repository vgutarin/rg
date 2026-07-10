package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationStatusComponentTest {

    @Test
    void show_allSevenStates_areMutuallyExclusiveLocalizedAndPolitelyAnnounced() {
        var component = component(() -> { });

        for (var state : AuthorizationUiState.values()) {
            component.show(state);

            assertThat(component.state()).isEqualTo(state);
            assertThat(component.getElement().getAttribute("data-authorization-state"))
                    .isEqualTo(state.name().toLowerCase(java.util.Locale.ROOT));
            assertThat(renderedText(component)).isNotBlank().doesNotContain("auth.");
            assertThat(component.getElement().getAttribute("role")).isEqualTo("status");
            assertThat(component.getElement().getAttribute("aria-live")).isEqualTo("polite");
        }
    }

    @Test
    void show_terminalState_removesPreviouslyPermittedContentBeforeRenderingFailure() {
        var component = component(() -> { });
        var protectedContent = new Span("protected-marker");
        component.show(AuthorizationUiState.PERMITTED, protectedContent);

        assertThat(descendants(component)).contains(protectedContent);

        component.show(AuthorizationUiState.DENIED);

        assertThat(descendants(component)).doesNotContain(protectedContent);
        assertThat(renderedText(component)).doesNotContain("protected-marker");
    }

    @Test
    void retry_onlyUnavailableStateOffersIt_andActivationTransitionsToDisabledRetrying() {
        var attempts = new AtomicInteger();
        var component = component(attempts::incrementAndGet);

        for (var state : AuthorizationUiState.values()) {
            component.show(state);
            var retry = descendant(component, Button.class);
            assertThat(retry.isVisible()).isEqualTo(
                    state == AuthorizationUiState.TEMPORARILY_UNAVAILABLE
                            || state == AuthorizationUiState.RETRYING);
            assertThat(retry.isEnabled())
                    .isEqualTo(state == AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
        }

        component.show(AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
        descendant(component, Button.class).click();

        assertThat(component.state()).isEqualTo(AuthorizationUiState.RETRYING);
        assertThat(descendant(component, Button.class).isEnabled()).isFalse();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void show_terminalFailure_headingCanReceiveProgrammaticFocus() {
        var component = component(() -> { });

        component.show(AuthorizationUiState.INCOMPATIBLE);

        var heading = descendant(component, H1.class);
        assertThat(heading.getElement().getAttribute("tabindex")).isEqualTo("-1");
        assertThat(heading.getElement().getAttribute("aria-label")).isNotBlank();
    }

    @Test
    void denialViews_useReusableSemanticStatesWithoutDiagnosticContent() {
        var noAccess = descendant(new NoAccessView(localization()), AuthorizationStatusComponent.class);
        var denied = descendant(new AccessDeniedErrorView(localization()), AuthorizationStatusComponent.class);

        assertThat(noAccess.state()).isEqualTo(AuthorizationUiState.NO_ACCESS);
        assertThat(denied.state()).isEqualTo(AuthorizationUiState.DENIED);
        assertThat(renderedText(noAccess) + renderedText(denied))
                .doesNotContain("sub", "permission", "exception", "upstream", "auth_date");
    }

    private AuthorizationStatusComponent component(Runnable retryAction) {
        return new AuthorizationStatusComponent(localization(), retryAction);
    }

    private LocalizationService localization() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new LocalizationService(messages);
    }

    private String renderedText(Component component) {
        var ownText = component instanceof HasText textComponent ? textComponent.getText() : "";
        return ownText + component.getChildren().map(this::renderedText).reduce("", String::concat);
    }

    private java.util.List<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child), descendants(child).stream()))
                .toList();
    }

    private <T extends Component> T descendant(Component component, Class<T> type) {
        return descendants(component).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}
