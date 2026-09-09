package vg.rg.service.security;

import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.model.security.TelegramInitDataRequest;

public interface SecureAuthorizationFacade {

    AuthorizationOutcome redeemAuthorizationGrant(TelegramInitDataRequest request);
}
