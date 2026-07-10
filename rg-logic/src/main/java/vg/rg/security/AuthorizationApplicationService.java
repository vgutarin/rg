package vg.rg.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;

import java.util.UUID;

@Service
public final class AuthorizationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationApplicationService.class);

    private final SecureAuthorizationFacade facade;
    private final TelegramAuthorizationRequestValidator requestValidator;

    public AuthorizationApplicationService(
            SecureAuthorizationFacade facade,
            TelegramAuthorizationRequestValidator requestValidator) {
        this.facade = java.util.Objects.requireNonNull(facade);
        this.requestValidator = java.util.Objects.requireNonNull(requestValidator);
    }

    public AuthorizationOutcome redeem(TelegramInitDataRequest request) {
        var requestId = UUID.randomUUID();
        var outcome = requestValidator.isValid(request)
                ? facade.redeemAuthorizationGrant(request)
                : AuthorizationOutcome.invalidRequest();
        if (outcome == null) {
            log.warn("Secure authorization request {} produced no outcome", requestId);
            return AuthorizationOutcome.incompatible();
        }
        log.info("Secure authorization request {} outcome {}", requestId, outcome.status());
        return outcome;
    }
}
