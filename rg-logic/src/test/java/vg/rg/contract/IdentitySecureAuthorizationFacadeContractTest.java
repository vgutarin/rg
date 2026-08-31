package vg.rg.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.unique.id.model.UniqueId;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.security.SecureAuthorizationFacade;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;
import vg.rg.security.model.Permissions;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentitySecureAuthorizationFacadeContractTest extends SecureAuthorizationFacadeContract {

    @Mock IdentityApplicationApi identityApplicationApi;
    private SecureAuthorizationFacade facade;

    @BeforeEach
    void configureFacade() {
        when(identityApplicationApi.authenticateTelegram(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, vg.identity.model.TelegramAuthenticationRequest.class);
            if ("malformed".equals(request.telegramInitData())) {
                return Optional.empty();
            }
            return Optional.of(new IdentityApplicationUserPrincipal(
                    new UniqueId(91L), null, Set.of(Permissions.Location.VIEW), false));
        });
        var limits = new IdentityAuthorizationLimitsProperties(new MockEnvironment());
        facade = new IdentitySecureAuthorizationFacade(
                identityApplicationApi, new IdentityAuthorizationResponseValidator(limits));
    }

    @Override
    protected SecureAuthorizationFacade facade() {
        return facade;
    }
}
