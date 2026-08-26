package vg.rg.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

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
        authenticate(Set.of(Permissions.Location.VIEW, Permissions.Request.SUBMIT));

        assertThat(checker.hasAuthority(Permissions.Location.VIEW)).isTrue();
    }

    @Test
    void hasAuthority_missingPermission_returnsFalse() {
        authenticate(Set.of(Permissions.Location.VIEW));

        assertThat(checker.hasAuthority(Permissions.Reports.VIEW)).isFalse();
    }

    @Test
    void hasAuthority_unknownPermission_returnsFalse() {
        authenticate(Set.of(Permissions.Location.VIEW));

        assertThat(checker.hasAuthority("unknown:view")).isFalse();
    }

    @Test
    void currentUserUniqueId_authenticatedPrincipal_returnsPrincipalUserUniqueId() {
        authenticate(Set.of(Permissions.Location.VIEW));

        assertThat(checker.currentUserUniqueId()).contains(new UniqueId(1234L));
    }

    @Test
    void currentAuthenticationFlow_authenticatedPrincipal_returnsFlow() {
        authenticate(Set.of(Permissions.Location.VIEW));

        assertThat(checker.currentAuthenticationFlow()).contains(AuthenticationFlow.TELEGRAM);
    }

    @Test
    void currentAuthenticationFlow_missingAuthentication_isEmpty() {
        assertThat(checker.currentAuthenticationFlow()).isEmpty();
    }

    @Test
    void hasAuthority_missingAuthentication_returnsFalse() {
        assertThat(checker.hasAuthority(Permissions.Location.VIEW)).isFalse();
    }

    @Test
    void hasAuthority_nullSubject_deniesEveryRecognizedPermission() {
        authenticate(null, true, Permissions.ALL);

        assertThat(Permissions.ALL).allSatisfy(permission ->
                assertThat(checker.hasAuthority(permission)).isFalse());
        assertThat(checker.currentUserUniqueId()).isEmpty();
    }

    @Test
    void hasAuthority_falseConsentWithSubject_retainsPermission() {
        authenticate(new UniqueId(777L), false, Set.of(Permissions.Location.VIEW));

        assertThat(checker.hasAuthority(Permissions.Location.VIEW)).isTrue();
        assertThat(checker.currentUserUniqueId()).contains(new UniqueId(777L));
    }

    @Test
    void hasAuthority_wrongPrincipalTypeOrUnauthenticatedToken_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("malformed", null, List.of()));
        assertThat(checker.hasAuthority(Permissions.Location.VIEW)).isFalse();

        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), null, Set.of(Permissions.Location.VIEW), true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThat(checker.hasAuthority(Permissions.Location.VIEW)).isFalse();
    }

    private void authenticate(Set<String> permissions) {
        authenticate(new UniqueId(1234L), true, permissions);
    }

    private void authenticate(UniqueId userUniqueId, boolean consent, Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                userUniqueId, "Test User", permissions, consent, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
