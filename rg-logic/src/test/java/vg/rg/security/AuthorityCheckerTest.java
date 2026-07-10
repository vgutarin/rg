package vg.rg.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityCheckerTest {

    private final AuthorityChecker checker = new AuthorityChecker();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasAuthority_grantedPermission_returnsTrue() {
        authenticate(Set.of(Permissions.Home.VIEW, Permissions.Request.SUBMIT));

        assertThat(checker.hasAuthority(Permissions.Home.VIEW)).isTrue();
    }

    @Test
    void hasAuthority_missingPermission_returnsFalse() {
        authenticate(Set.of(Permissions.Home.VIEW));

        assertThat(checker.hasAuthority(Permissions.Reports.VIEW)).isFalse();
    }

    @Test
    void hasAuthority_unknownPermission_returnsFalse() {
        authenticate(Set.of(Permissions.Home.VIEW));

        assertThat(checker.hasAuthority("unknown:view")).isFalse();
    }

    @Test
    void currentSubject_authenticatedPrincipal_returnsPrincipalSubject() {
        authenticate(Set.of(Permissions.Home.VIEW));

        assertThat(checker.currentSubject()).contains("subject-1234");
    }

    @Test
    void hasAuthority_missingAuthentication_returnsFalse() {
        assertThat(checker.hasAuthority(Permissions.Home.VIEW)).isFalse();
    }

    @Test
    void hasAuthority_nullSubject_deniesEveryRecognizedPermission() {
        authenticate(null, true, Permissions.ALL);

        assertThat(Permissions.ALL).allSatisfy(permission ->
                assertThat(checker.hasAuthority(permission)).isFalse());
        assertThat(checker.currentSubject()).isEmpty();
    }

    @Test
    void hasAuthority_falseConsentWithSubject_retainsPermission() {
        authenticate("opaque-subject", false, Set.of(Permissions.Home.VIEW));

        assertThat(checker.hasAuthority(Permissions.Home.VIEW)).isTrue();
        assertThat(checker.currentSubject()).contains("opaque-subject");
    }

    @Test
    void hasAuthority_wrongPrincipalTypeOrUnauthenticatedToken_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("malformed", null, List.of()));
        assertThat(checker.hasAuthority(Permissions.Home.VIEW)).isFalse();

        var principal = new AuthenticatedUserPrincipal(
                "subject-1234", null, Set.of(Permissions.Home.VIEW), true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThat(checker.hasAuthority(Permissions.Home.VIEW)).isFalse();
    }

    private void authenticate(Set<String> permissions) {
        authenticate("subject-1234", true, permissions);
    }

    private void authenticate(String subject, boolean consent, Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                subject, "Test User", permissions, consent, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
