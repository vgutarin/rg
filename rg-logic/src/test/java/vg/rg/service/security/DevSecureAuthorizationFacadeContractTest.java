package vg.rg.service.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.model.security.Permissions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DevSecureAuthorizationFacadeContractTest extends SecureAuthorizationFacadeContract {

    private final SecureAuthorizationFacade facade = new DevSecureAuthorizationFacade(
            new TelegramInitDataVerifier(new ObjectMapper(), SecureAuthorizationFixtures.CLOCK,
                    SecureAuthorizationFixtures.SYNTHETIC_BOT_TOKEN,
                    Duration.ofHours(1), Duration.ofSeconds(30)));

    @Override
    protected SecureAuthorizationFacade facade() {
        return facade;
    }

    @Test
    void redeemAuthorizationGrant_identityPermissionSettings_doNotChangeDevelopmentCatalog() {
        System.setProperty("rg.secure-service.identity.max-permission-count", "1");
        System.setProperty("rg.secure-service.identity.max-permission-length", "1");
        try {
            var outcome = facade.redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                    SecureAuthorizationFixtures.signedInitData(91, SecureAuthorizationFixtures.NOW)));

            assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
            assertThat(outcome.principal().orElseThrow().permissions())
                    .containsExactlyInAnyOrderElementsOf(Permissions.ALL);
        } finally {
            System.clearProperty("rg.secure-service.identity.max-permission-count");
            System.clearProperty("rg.secure-service.identity.max-permission-length");
        }
    }
}
