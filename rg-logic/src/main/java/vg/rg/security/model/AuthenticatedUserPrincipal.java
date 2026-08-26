package vg.rg.security.model;

import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

@Builder
public record AuthenticatedUserPrincipal(
        UniqueId userUniqueId,
        String name,
        Set<String> permissions,
        boolean consentGiven,
        AuthenticationFlow authenticationFlow) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public AuthenticatedUserPrincipal {
        if (name != null) {
            ContractValidation.bounded(name, "name", 1, 256);
        }
        ContractValidation.required(permissions, "permissions");
        ContractValidation.required(authenticationFlow, "authenticationFlow");
        var copy = new LinkedHashSet<String>();
        for (var permission : permissions) {
            ContractValidation.required(permission, "permission");
            if (!Permissions.hasValidFormat(permission)) {
                throw new IllegalArgumentException("permission has invalid syntax");
            }
            copy.add(permission);
        }
        permissions = Set.copyOf(copy);
    }
}
