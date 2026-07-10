package vg.rg.security;

import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;

public interface SecureAuthorizationFacade {

    AuthorizationOutcome redeemAuthorizationGrant(TelegramInitDataRequest request);
}
