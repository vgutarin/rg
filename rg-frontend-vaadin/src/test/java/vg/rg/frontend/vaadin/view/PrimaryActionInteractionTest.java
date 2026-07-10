package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.service.ProtectedActionService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrimaryActionInteractionTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock AuthenticationContext authenticationContext;
    @Mock ProtectedActionService protectedActionService;
    @Mock BeforeEnterEvent event;

    @Test
    void permittedPrimaryAction_isLabeledFocusableAndStartsWithinOneActivationUsingOneKey() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Home.VIEW)).thenReturn(true);
        when(authorityChecker.hasAuthority(Permissions.Request.SUBMIT)).thenReturn(true);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(new AuthenticatedUserPrincipal(
                        "subject-1234", null,
                        Set.of(Permissions.Home.VIEW, Permissions.Request.SUBMIT),
                        true, AuthenticationFlow.TELEGRAM)));
        when(protectedActionService.submit(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(invocation -> {
                    var key = invocation.getArgument(0, UUID.class);
                    return new ProtectedActionService.Result(
                            UUID.randomUUID(), key, ProtectedActionService.State.COMPLETED,
                            "request.submitted");
                });
        var landing = new LandingView(
                localization, authorityChecker, authenticationContext, protectedActionService);
        landing.beforeEnter(event);
        var action = descendants(landing).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(action.getText()).isEqualTo("request.submit");
        assertThat(action.isVisible()).isTrue();
        assertThat(action.isEnabled()).isTrue();
        assertThat(action.getElement().getAttribute("tabindex")).isNotEqualTo("-1");

        action.click();
        action.click();

        var keys = ArgumentCaptor.forClass(UUID.class);
        verify(protectedActionService, times(2)).submit(keys.capture());
        assertThat(keys.getAllValues()).hasSize(2).containsOnly(keys.getValue());
    }

    private java.util.List<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child), descendants(child).stream()))
                .toList();
    }
}
