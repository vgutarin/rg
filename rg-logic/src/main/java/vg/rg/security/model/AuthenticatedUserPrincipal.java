package vg.rg.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public record AuthenticatedUserPrincipal(
        String sub,
        String name,
        Set<String> permissions,
        boolean consentGiven,
        AuthenticationFlow authenticationFlow) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public AuthenticatedUserPrincipal {
        if (sub != null) {
            ContractValidation.bounded(sub, "sub", 1, 128);
        }
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
