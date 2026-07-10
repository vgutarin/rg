package vg.rg.security.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vg.rg.security.model.Permissions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public final class IdentityAuthorizationResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(IdentityAuthorizationResponseValidator.class);

    private final IdentityAuthorizationLimitsProperties limits;

    public IdentityAuthorizationResponseValidator(IdentityAuthorizationLimitsProperties limits) {
        this.limits = java.util.Objects.requireNonNull(limits);
    }

    public Optional<Set<String>> validate(Collection<String> permissions) {
        if (permissions == null) {
            return Optional.empty();
        }
        var unique = new LinkedHashSet<String>();
        var duplicateFound = false;
        for (var permission : permissions) {
            if (!Permissions.hasValidFormat(permission)
                    || permission.length() > limits.maxPermissionLength()) {
                return Optional.empty();
            }
            if (!unique.add(permission)) {
                duplicateFound = true;
            }
        }
        if (duplicateFound) {
            log.warn("Identity authorization returned duplicate permissions; duplicates ignored");
        }
        if (unique.size() > limits.maxPermissionCount()) {
            return Optional.empty();
        }
        return Optional.of(Collections.unmodifiableSet(unique));
    }
}
