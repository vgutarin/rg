package vg.rg.security.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.security.SecureAuthorizationFacade;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;

import java.util.Collections;
import java.util.Objects;

@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "rg.secure-service", name = "enabled", havingValue = "false", matchIfMissing = true)
public class IdentitySecureAuthorizationFacade implements SecureAuthorizationFacade {

    private final IdentityApplicationApi identityApplicationApi;
    private final IdentityAuthorizationResponseValidator responseValidator;

    @Autowired
    public IdentitySecureAuthorizationFacade(
            IdentityApplicationApi identityApplicationApi,
            IdentityAuthorizationResponseValidator responseValidator) {
        this.identityApplicationApi = Objects.requireNonNull(identityApplicationApi);
        this.responseValidator = Objects.requireNonNull(responseValidator);
    }

    @Override
    public AuthorizationOutcome redeemAuthorizationGrant(TelegramInitDataRequest request) {
        Objects.requireNonNull(request);
        final IdentityApplicationUserPrincipal principal;
        try {
            var identityRequest = new TelegramAuthenticationRequest(request.initData(), false);
            var result = identityApplicationApi.authenticateTelegram(identityRequest);
            if (result == null) {
                return AuthorizationOutcome.incompatible();
            }
            if (result.isEmpty()) {
                return AuthorizationOutcome.invalidRequest();
            }
            principal = result.orElseThrow();
        } catch (RuntimeException exception) {
            return AuthorizationOutcome.unavailable(null);
        }

        try {
            var validatedPermissions = responseValidator.validate(principal.permissions());
            if (validatedPermissions.isEmpty()) {
                return AuthorizationOutcome.incompatible();
            }
            var permissions = validatedPermissions.orElseThrow();
            if (principal.sub() == null
                    && permissions != null
                    && !permissions.isEmpty()) {
                permissions = Collections.emptySet();
                log.warn("Identity authorization returned permissions without a subject; permissions ignored");
            }
            return AuthorizationOutcome.authorized(new AuthenticatedUserPrincipal(
                    principal.sub(),
                    principal.name(),
                    permissions,
                    principal.consentGiven(),
                    AuthenticationFlow.TELEGRAM));
        } catch (RuntimeException exception) {
            return AuthorizationOutcome.incompatible();
        }
    }
}
