package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedActionMethodSecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submit_missingAuthentication_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(ProtectedActionService.class);

            assertThatThrownBy(() -> service.submit(UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void submit_missingRequiredPermission_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(ProtectedActionService.class);

            authenticate(Set.of(Permissions.Home.VIEW));
            assertThatThrownBy(() -> service.submit(UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void submit_grantedPermission_returnsIdempotentCompletedResult() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(ProtectedActionService.class);

            authenticate(Set.of(Permissions.Request.SUBMIT));
            var key = UUID.randomUUID();
            var first = service.submit(key);
            assertThat(service.submit(key)).isEqualTo(first);
            assertThat(first.state()).isEqualTo(ProtectedActionService.State.COMPLETED);
            assertThat(context.getBean(AtomicInteger.class)).hasValue(1);
        }
    }

    @Test
    void submit_nullSubjectWithPermission_throwsAccessDeniedBeforeEffect() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(ProtectedActionService.class);

            authenticate(null, Set.of(Permissions.Request.SUBMIT));

            assertThatThrownBy(() -> service.submit(UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(context.getBean(AtomicInteger.class)).hasValue(0);
        }
    }

    private void authenticate(Set<String> permissions) {
        authenticate("subject-1234", permissions);
    }

    private void authenticate(String subject, Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                subject, "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        AuthorityChecker authorityChecker() {
            return new AuthorityChecker();
        }

        @Bean
        AtomicInteger effectCount() {
            return new AtomicInteger();
        }

        @Bean
        ProtectedActionService protectedActionService(
                AuthorityChecker authorityChecker, AtomicInteger effectCount) {
            return new ProtectedActionServiceImpl(
                    authorityChecker, (operationId, subject) -> effectCount.incrementAndGet());
        }
    }
}
