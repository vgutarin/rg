package vg.rg.security.dev;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.security.support.SecureAuthorizationFixtures;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class DevSecureAuthorizationFacadeTest {

    @Test
    void redeemAuthorizationGrant_validTelegramData_returnsTelegramSubject() {
        var facade = facade();

        var outcome = facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(42, SecureAuthorizationFixtures.NOW)));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
        assertThat(outcome.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.sub()).isEqualTo("42");
            assertThat(principal.name()).isNull();
            assertThat(principal.permissions()).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
            assertThat(principal.consentGiven()).isTrue();
            assertThat(principal.authenticationFlow()).isEqualTo(AuthenticationFlow.TELEGRAM);
        });
    }

    @Test
    void redeemAuthorizationGrant_validTelegramData_returnsAllRecognizedPermissions() {
        var facade = facade();

        var outcome = facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(42, SecureAuthorizationFixtures.NOW)));

        assertThat(outcome.principal().orElseThrow().permissions())
                .containsExactlyInAnyOrderElementsOf(Permissions.ALL);
    }

    @Test
    void redeemAuthorizationGrant_repeatedTelegramData_remainsAuthorized() {
        var facade = facade();
        var request = SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(42, SecureAuthorizationFixtures.NOW));
        facade.redeemAuthorizationGrant(request);

        var repeated = facade.redeemAuthorizationGrant(request);

        assertThat(repeated.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
    }

    @Test
    void redeemAuthorizationGrant_differentVerifiedUser_doesNotRetainPriorIdentity() {
        var facade = facade();
        facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(42, SecureAuthorizationFixtures.NOW)));

        var outcome = facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(84, SecureAuthorizationFixtures.NOW)));

        assertThat(outcome.principal()).hasValueSatisfying(principal -> {
            assertThat(principal.sub()).isEqualTo("84");
            assertThat(principal.name()).isNull();
        });
    }

    @Test
    void redeemAuthorizationGrant_invalidInput_returnsInvalidRequest() {
        var facade = facade();

        assertThat(facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request("invalid")).status())
                .isEqualTo(AuthorizationOutcome.Status.INVALID_REQUEST);
    }

    @Test
    void redeemAuthorizationGrant_expiredInput_returnsExpired() {
        var facade = facade();

        assertThat(facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(42, SecureAuthorizationFixtures.NOW.minusSeconds(3601))
        )).status()).isEqualTo(AuthorizationOutcome.Status.EXPIRED);
    }

    private DevSecureAuthorizationFacade facade() {
        var verifier = new TelegramInitDataVerifier(
                new ObjectMapper(), SecureAuthorizationFixtures.CLOCK,
                SecureAuthorizationFixtures.SYNTHETIC_BOT_TOKEN,
                Duration.ofHours(1), Duration.ofSeconds(30));
        return new DevSecureAuthorizationFacade(verifier);
    }
}
