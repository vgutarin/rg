package vg.rg.security.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApi;
import vg.unique.id.model.UniqueId;
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
        var userUniqueId = new UniqueId(42L);
        var principal = new IdentityApplicationUserPrincipal(
                userUniqueId, "Test User",
                Set.of(Permissions.Reports.READ, "unknown:view"), true);
        when(identityApplicationApi.authenticateTelegram(argThat(request ->
                REQUEST.initData().equals(request.telegramInitData())
                        && !request.consentToKeepPersonalData())))
                .thenReturn(Optional.of(principal));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
        var authenticatedPrincipal = outcome.principal().orElseThrow();
        assertThat(authenticatedPrincipal.userUniqueId()).isEqualTo(userUniqueId);
        assertThat(authenticatedPrincipal.name()).isEqualTo("Test User");
        assertThat(authenticatedPrincipal.permissions()).containsExactlyInAnyOrder(
                Permissions.Reports.READ, "unknown:view");
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
            assertThat(principal.userUniqueId()).isNull();
            assertThat(principal.consentGiven()).isFalse();
        });
    }

    @Test
    void redeemAuthorizationGrant_provisionalPrincipal_ignoresPermissions(
            CapturedOutput output) {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        null, "Sensitive Name", Set.of("reports:read", "request:submit"), false)));

        var outcome = facade().redeemAuthorizationGrant(REQUEST);

        assertThat(outcome.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.userUniqueId()).isNull();
            assertThat(principal.name()).isEqualTo("Sensitive Name");
            assertThat(principal.permissions()).isEmpty();
            assertThat(principal.consentGiven()).isFalse();
        });
        assertThat(output.getAll())
                .containsOnlyOnce("Identity authorization returned permissions without a subject")
                .doesNotContain("Sensitive Name", "reports:read", "request:submit");
    }

    @Test
    void redeemAuthorizationGrant_nullResult_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any())).thenReturn(null);

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_malformedPrincipal_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        new UniqueId(42L), null, Set.of("malformed"), true)));

        assertThat(facade().redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_permissionCountOverConfiguredLimit_returnsIncompatible() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        new UniqueId(42L), null, Set.of("reports:read", "request:submit"), true)));

        assertThat(facade(1, 128).redeemAuthorizationGrant(REQUEST).status())
                .isEqualTo(AuthorizationOutcome.Status.INCOMPATIBLE);
    }

    @Test
    void redeemAuthorizationGrant_permissionAtConfiguredBound_preservesPermission() {
        when(identityApplicationApi.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        new UniqueId(42L), null, Set.of("alpha:read"), true)));

        assertThat(facade(1, 10).redeemAuthorizationGrant(REQUEST).principal())
                .hasValueSatisfying(principal -> assertThat(principal.permissions())
                        .containsExactly("alpha:read"));
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
        var limits = new IdentityAuthorizationLimitsProperties(null, null);
        return new IdentitySecureAuthorizationFacade(
                identityApplicationApi, new IdentityAuthorizationResponseValidator(limits));
    }

    private IdentitySecureAuthorizationFacade facade(int count, int length) {
        var limits = new IdentityAuthorizationLimitsProperties(
                Integer.toString(count), Integer.toString(length));
        return new IdentitySecureAuthorizationFacade(
                identityApplicationApi, new IdentityAuthorizationResponseValidator(limits));
    }
}
