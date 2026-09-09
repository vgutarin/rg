package vg.rg.service.security;

import org.junit.jupiter.api.Test;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.AuthorizationOutcome;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class SecureAuthorizationFacadeContract {

    protected abstract SecureAuthorizationFacade facade();

    @Test
    void redeemAuthorizationGrant_validAuthorization_returnsAuthenticatedPrincipal() {
        var outcome = facade().redeemAuthorizationGrant(SecureAuthorizationFixtures.request(
                SecureAuthorizationFixtures.signedInitData(91, SecureAuthorizationFixtures.NOW)));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
        assertThat(outcome.principal()).isPresent();
        assertThat(outcome.principal().orElseThrow().authenticationFlow())
                .isEqualTo(AuthenticationFlow.TELEGRAM);
        assertThat(outcome.principal().orElseThrow().permissions()).isNotNull();
    }

    @Test
    void redeemAuthorizationGrant_malformedRequest_returnsInvalidRequest() {
        assertThat(facade().redeemAuthorizationGrant(SecureAuthorizationFixtures.request("malformed")).status())
                .isEqualTo(AuthorizationOutcome.Status.INVALID_REQUEST);
    }
}
