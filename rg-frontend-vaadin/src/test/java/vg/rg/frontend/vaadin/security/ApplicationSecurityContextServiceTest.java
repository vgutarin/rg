package vg.rg.frontend.vaadin.security;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;

import java.util.Set;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ApplicationSecurityContextServiceTest {

    @Mock AuthenticationEventPublisher events;
    @Mock VaadinSession vaadinSession;
    @Mock WrappedSession wrappedSession;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_validPrincipal_installsAuthenticatedPrincipal() {
        var principal = principal(Set.of("home:view"));

        new ApplicationSecurityContextService(events).authenticate(principal);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(principal.sub()).isEqualTo("subject-1234");
    }

    @Test
    void authenticate_validPrincipal_preservesIdentityFields() {
        new ApplicationSecurityContextService(events).authenticate(principal(Set.of("home:view")));

        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).extracting("sub", "name", "consentGiven", "authenticationFlow")
                .containsExactly("subject-1234", "Test User", true, AuthenticationFlow.TELEGRAM);
    }

    @Test
    void authenticate_unknownAuthority_installsOnlyRecognizedAuthority() {
        new ApplicationSecurityContextService(events).authenticate(
                principal(Set.of("home:view", "unknown:view")));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("home:view");
    }

    @Test
    void authenticate_validPrincipal_publishesAuthenticationSuccess() {
        new ApplicationSecurityContextService(events).authenticate(principal(Set.of("home:view")));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var captor = ArgumentCaptor.forClass(Authentication.class);
        verify(events).publishAuthenticationSuccess(captor.capture());
        assertThat(captor.getValue()).isSameAs(authentication);
    }

    @Test
    void authenticate_currentVaadinSession_persistsSecurityContext() {
        var principal = principal(Set.of("reports:view"));
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(vaadinSession);

            new ApplicationSecurityContextService(events).authenticate(principal);

            var context = ArgumentCaptor.forClass(SecurityContext.class);
            verify(wrappedSession).setAttribute(
                    eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                    context.capture());
            assertThat(context.getValue().getAuthentication().getPrincipal())
                    .isSameAs(principal);
            assertThat(context.getValue().getAuthentication().getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("reports:view");
        }
    }

    @Test
    void authenticate_principal_serializationPreservesEveryIdentityField() throws Exception {
        var original = new AuthenticatedUserPrincipal(
                null, "Session Name", Set.of("home:view"), false, AuthenticationFlow.TELEGRAM);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        AuthenticatedUserPrincipal restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AuthenticatedUserPrincipal) input.readObject();
        }

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void authenticate_nullSubject_installsPrincipalWithZeroAuthorities() {
        var principal = new AuthenticatedUserPrincipal(
                null, "Session Name", Set.of("home:view"), false, AuthenticationFlow.TELEGRAM);

        new ApplicationSecurityContextService(events).authenticate(principal);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void authenticate_falseConsentWithSubject_retainsRecognizedAuthorities() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1234", "Session Name", Set.of("home:view", "unknown:view"), false,
                AuthenticationFlow.TELEGRAM);

        new ApplicationSecurityContextService(events).authenticate(principal);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("home:view");
    }

    @Test
    void authenticate_failedReplacement_clearsPriorThreadAndSessionState() {
        var prior = UsernamePasswordAuthenticationToken.authenticated("prior", null, Set.of());
        SecurityContextHolder.getContext().setAuthentication(prior);
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        doThrow(new IllegalStateException("synthetic persistence failure"))
                .when(wrappedSession).setAttribute(
                        eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                        org.mockito.ArgumentMatchers.any(SecurityContext.class));
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(vaadinSession);

            assertThatThrownBy(() -> new ApplicationSecurityContextService(events)
                    .authenticate(principal(Set.of("home:view"))))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(wrappedSession, atLeastOnce()).removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    @Test
    void clear_removesThreadAndSessionIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal(Set.of("home:view")), null, Set.of()));
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(vaadinSession);

            new ApplicationSecurityContextService(events).clear();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(wrappedSession).removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    @Test
    void authenticate_successfulReplacement_keepsOldPermissionsUntilNewContextIsPersisted() {
        var oldPrincipal = principal(Set.of(Permissions.Home.VIEW));
        var oldAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                oldPrincipal, null, Set.of());
        SecurityContextHolder.getContext().setAuthentication(oldAuthentication);
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(oldAuthentication);
            return null;
        }).when(wrappedSession).setAttribute(
                eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                org.mockito.ArgumentMatchers.any(SecurityContext.class));
        var replacement = principal(Set.of(Permissions.Reports.VIEW));
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(vaadinSession);

            new ApplicationSecurityContextService(events).authenticate(replacement);

            var installed = SecurityContextHolder.getContext().getAuthentication();
            assertThat(installed.getPrincipal()).isSameAs(replacement);
            assertThat(installed.getAuthorities()).extracting(Object::toString)
                    .containsExactly(Permissions.Reports.VIEW);
            verify(wrappedSession, never()).removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    @Test
    void persistedAuthentication_reloadAndLocaleIndependentStateRetainsSamePrincipal() {
        var principal = principal(Set.of(Permissions.Home.VIEW));
        when(vaadinSession.getSession()).thenReturn(wrappedSession);
        var persisted = ArgumentCaptor.forClass(SecurityContext.class);
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(vaadinSession);
            new ApplicationSecurityContextService(events).authenticate(principal);
            verify(wrappedSession).setAttribute(
                    eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY), persisted.capture());

            SecurityContextHolder.clearContext();
            SecurityContextHolder.setContext(persisted.getValue());

            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isSameAs(principal);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly(Permissions.Home.VIEW);
        }
    }

    private AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                "subject-1234", "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }
}
