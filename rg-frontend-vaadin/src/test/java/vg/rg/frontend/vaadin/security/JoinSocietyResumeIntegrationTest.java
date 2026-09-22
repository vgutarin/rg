package vg.rg.frontend.vaadin.security;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import vg.rg.config.security.SecureAuthorizationLimitsProperties;
import vg.rg.frontend.vaadin.service.ApplicationSecurityContextService;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.TelegramAuthView;
import vg.rg.frontend.vaadin.view.home.JoinSocietyIntent;
import vg.rg.frontend.vaadin.view.home.LandingView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.model.security.TelegramInitDataRequest;
import vg.rg.service.resource.ResourcePublicationService;
import vg.rg.service.security.AuthorizationApplicationService;
import vg.rg.service.security.SecureAuthorizationFacade;
import vg.rg.service.security.TelegramAuthorizationRequestValidator;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.unique.id.model.UniqueId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(JoinSocietyResumeIntegrationTest.TestConfiguration.class)
@ExtendWith(MockitoExtension.class)
class JoinSocietyResumeIntegrationTest {

    private static final UniqueId EVENT = new UniqueId(456L);
    private static final String INIT_DATA = "auth_date=1&hash=synthetic";
    @Autowired AuthorizationApplicationService authorization;
    @Autowired ApplicationSecurityContextService securityContext;
    @Autowired SecureAuthorizationFacade facade;
    @Autowired LocalizationService localization;
    @Mock ResourcePublicationService publications;
    @Mock WorkspaceMembershipRequestService membership;
    @Mock VaadinSession session;
    @Mock WrappedSession httpSession;
    @Mock UI ui;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, Object> httpAttributes = new HashMap<>();

    @BeforeEach
    void attachSession() {
        lenient().when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        lenient().doAnswer(call -> {
            var key = call.<String>getArgument(0);
            var value = call.getArgument(1);
            if (value == null) attributes.remove(key);
            else attributes.put(key, value);
            return null;
        }).when(session).setAttribute(anyString(), nullable(Object.class));
        when(session.getSession()).thenReturn(httpSession);
        lenient().doAnswer(call -> {
            httpAttributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(httpSession).setAttribute(anyString(), any());
        lenient().doAnswer(call -> { httpAttributes.remove(call.getArgument(0)); return null; })
                .when(httpSession).removeAttribute(anyString());
        lenient().when(ui.getPage()).thenReturn(mock(Page.class));
        VaadinSession.setCurrent(session);
        UI.setCurrent(ui);
    }

    @AfterEach
    void detachSession() {
        SecurityContextHolder.clearContext();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
        reset(facade);
    }

    @Test
    void consentUpgrade_persistsTheSubjectAndResumesOnlyTheSelectedSessionEventOnce() {
        var requests = new ArrayList<TelegramInitDataRequest>();
        when(facade.redeemAuthorizationGrant(any())).thenAnswer(call -> {
            var request = call.<TelegramInitDataRequest>getArgument(0);
            requests.add(request);
            return AuthorizationOutcome.authorized(principal(request.consentToKeepPersonalData()
                    ? new UniqueId(123L) : null));
        });
        login().authenticate(INIT_DATA);
        assertThat(new AuthenticationContext().getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .hasValueSatisfying(principal -> assertThat(principal.userUniqueId()).isNull());
        JoinSocietyIntent.remember(EVENT);
        VaadinSession.setCurrent(mock(VaadinSession.class));
        assertThat(JoinSocietyIntent.isPending()).isFalse();
        VaadinSession.setCurrent(session);

        login().authenticate(INIT_DATA);
        renderHomeTwice();

        assertThat(requests).extracting(TelegramInitDataRequest::consentToKeepPersonalData)
                .containsExactly(false, true);
        var persisted = (SecurityContext) httpAttributes.get(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(persisted.getAuthentication().getPrincipal()).isEqualTo(principal(new UniqueId(123L)));
        verify(membership).requestJoin(EVENT);
        assertThat(JoinSocietyIntent.isPending()).isFalse();
    }

    @Test
    void upgradeThatStillReturnsAProvisionalPrincipal_keepsIntentWithoutRequestingMembership() {
        JoinSocietyIntent.remember(EVENT);
        when(facade.redeemAuthorizationGrant(any())).thenReturn(AuthorizationOutcome.authorized(principal(null)));

        login().authenticate(INIT_DATA);
        renderHomeTwice();

        assertThat(JoinSocietyIntent.isPending()).isTrue();
        verifyNoInteractions(membership);
    }

    @ParameterizedTest
    @MethodSource("failedAuthorizationOutcomes")
    void failedUpgrade_keepsIntentAndClearsAuthentication(AuthorizationOutcome failure) {
        securityContext.authenticate(principal(null));
        JoinSocietyIntent.remember(EVENT);
        when(facade.redeemAuthorizationGrant(any())).thenReturn(failure);

        login().authenticate(INIT_DATA);

        assertThat(JoinSocietyIntent.isPending()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(httpAttributes).doesNotContainKey(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        verifyNoInteractions(membership);
    }

    @Test
    void resumedRequestRefusal_consumesIntentWithoutReplayingItOnRender() {
        JoinSocietyIntent.remember(EVENT);
        when(facade.redeemAuthorizationGrant(any()))
                .thenReturn(AuthorizationOutcome.authorized(principal(new UniqueId(123L))));
        doThrow(new EntityNotFoundException()).when(membership).requestJoin(EVENT);

        login().authenticate(INIT_DATA);
        renderHomeTwice();

        assertThat(JoinSocietyIntent.isPending()).isFalse();
        verify(membership).requestJoin(EVENT);
    }

    private void renderHomeTwice() {
        when(publications.browseWorkspaceEvents(any(PageRequest.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        try (var notification = mockStatic(Notification.class)) {
            var landing = new LandingView(localization, new AuthenticationContext(), publications, membership);
            landing.beforeEnter(mock(BeforeEnterEvent.class));
            landing.localeChange(null);
        }
    }

    private TelegramAuthView login() {
        return new TelegramAuthView(authorization, securityContext, localization);
    }

    private static AuthenticatedUserPrincipal principal(UniqueId subject) {
        return AuthenticatedUserPrincipal.builder().userUniqueId(subject).name("Requester").permissions(Set.of())
                .consentGiven(subject != null).authenticationFlow(AuthenticationFlow.TELEGRAM).build();
    }

    private static Stream<AuthorizationOutcome> failedAuthorizationOutcomes() {
        return Stream.of(AuthorizationOutcome.unavailable(Duration.ofSeconds(5)), AuthorizationOutcome.denied(),
                AuthorizationOutcome.incompatible(), AuthorizationOutcome.invalidRequest());
    }

    @Configuration
    @Import({AuthorizationApplicationService.class, TelegramAuthorizationRequestValidator.class,
            ApplicationSecurityContextService.class})
    static class TestConfiguration {
        @Bean SecureAuthorizationFacade facade() { return mock(SecureAuthorizationFacade.class); }
        @Bean AuthenticationEventPublisher authenticationEvents() { return mock(AuthenticationEventPublisher.class); }
        @Bean SecureAuthorizationLimitsProperties limits() { return new SecureAuthorizationLimitsProperties(null); }
        @Bean LocalizationService localization() {
            var messages = new ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            return new LocalizationService(messages);
        }
    }
}
