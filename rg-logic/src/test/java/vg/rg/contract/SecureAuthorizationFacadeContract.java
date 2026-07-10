package vg.rg.contract;

import org.junit.jupiter.api.Test;
import vg.rg.security.support.SecureAuthorizationFixtures;
import vg.rg.security.SecureAuthorizationFacade;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.AuthenticationFlow;

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
