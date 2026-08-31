package vg.rg.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Component
public final class AuthorityChecker {

    public boolean hasAuthority(String permission) {
        if (!Permissions.isRecognized(permission)) {
            return false;
        }
        return currentPrincipal()
                .map(principal -> principal.userUniqueId() != null
                        && principal.permissions().contains(permission))
                .orElse(false);
    }

    public Optional<UniqueId> currentUserUniqueId() {
        return currentPrincipal()
                .map(AuthenticatedUserPrincipal::userUniqueId);
    }

    public Optional<AuthenticationFlow> currentAuthenticationFlow() {
        return currentPrincipal()
                .map(AuthenticatedUserPrincipal::authenticationFlow);
    }

    public Optional<AuthenticatedUserPrincipal> currentPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }
}
