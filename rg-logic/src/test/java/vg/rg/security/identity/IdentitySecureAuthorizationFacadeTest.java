package vg.rg.security.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.security.model.TelegramInitDataRequest;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class IdentitySecureAuthorizationFacadeTest {

    private static final TelegramInitDataRequest REQUEST = new TelegramInitDataRequest("auth_date=1&hash=x");

    @Mock IdentityApplicationApi identityApplicationApi;

    @Test
    void redeemAuthorizationGrant_establishedIdentityPrincipal_returnsAuthenticatedPrincipal() {
        var subject = "subject-42";
        var principal = new IdentityApplicationUserPrincipal(
                subject, "Test User",
                Set.of(Permissions.Home.VIEW, "unknown:view"), true);
        when(identityApplicationApi.authenticateTelegram(argThat(request ->
                REQUEST.initData().equals(request.telegramInitData())
                        && !request.consentToKeepPersonalData())))
                .thenReturn(Optional.of(principal));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
        var authenticatedPrincipal = outcome.principal().orElseThrow();
        assertThat(authenticatedPrincipal.sub()).isEqualTo(subject);
        assertThat(authenticatedPrincipal.name()).isEqualTo("Test User");
        assertThat(authenticatedPrincipal.permissions()).containsExactlyInAnyOrder(
                Permissions.Home.VIEW, "unknown:view");
        assertThat(authenticatedPrincipal.consentGiven()).isTrue();
        assertThat(authenticatedPrincipal.authenticationFlow()).isEqualTo(AuthenticationFlow.TELEGRAM);
    }

    @Test
    void redeemAuthorizationGrant_unresolvedPrincipal_returnsInvalidRequest() {
        when(identityApplicationApi.authenticateTelegram(any())).thenReturn(Optional.empty());

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INVALID_REQUEST);
    }

    @Test
    void redeemAuthorizationGrant_provisionalPrincipal_returnsAuthenticatedPrincipal() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        null, "Ignored personal name", Set.of(), false)));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
        assertThat(outcome.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.sub()).isNull();
            assertThat(principal.consentGiven()).isFalse();
        });
    }

    @Test
    void redeemAuthorizationGrant_provisionalPrincipal_ignoresPermissions(
            CapturedOutput output) {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        null, "Sensitive Name", Set.of("home:view", "reports:view"), false)));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.sub()).isNull();
            assertThat(principal.name()).isEqualTo("Sensitive Name");
            assertThat(principal.permissions()).isEmpty();
            assertThat(principal.consentGiven()).isFalse();
        });
        assertThat(output.getAll())
                .containsOnlyOnce("Identity authorization returned permissions without a subject")
                .doesNotContain("Sensitive Name", "home:view", "reports:view");
    }

    @Test
    void redeemAuthorizationGrant_nullResult_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any())).thenReturn(null);

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_emptySubject_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        "", null, Set.of(), true)));

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_malformedPrincipal_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        "subject-42", null, Set.of("malformed"), true)));

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_permissionCountOverConfiguredLimit_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        "subject-42", null, Set.of("home:view", "reports:view"), true)));

        assertThat(facade(1, 128).redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_permissionAtConfiguredBound_preservesPermission() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        "subject-42", null, Set.of("alpha:view"), true)));

        assertThat(facade(1, 10).redeemAuthorizationGrant(REQUEST).principal())
                .hasValueSatisfying(principal -> assertThat(principal.permissions())
                        .containsExactly("alpha:view"));
    }

    @Test
    void redeemAuthorizationGrant_identityClientFailure_returnsUnavailableWithoutDetails() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenThrow(new IllegalStateException("synthetic sensitive upstream detail"));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.UNAVAILABLE);
        assertThat(outcome.toString()).doesNotContain("synthetic sensitive upstream detail");
    }

    private IdentitySecureAuthorizationFacade facade() {
        var limits = new IdentityAuthorizationLimitsProperties(new MockEnvironment());
        return new IdentitySecureAuthorizationFacade(
                identityApplicationApi, new IdentityAuthorizationResponseValidator(limits));
    }

    private IdentitySecureAuthorizationFacade facade(int count, int length) {
        var environment = new MockEnvironment()
                .withProperty(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_COUNT_PROPERTY,
                        Integer.toString(count))
                .withProperty(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_LENGTH_PROPERTY,
                        Integer.toString(length));
        var limits = new IdentityAuthorizationLimitsProperties(environment);
        return new IdentitySecureAuthorizationFacade(
                identityApplicationApi, new IdentityAuthorizationResponseValidator(limits));
    }
}
