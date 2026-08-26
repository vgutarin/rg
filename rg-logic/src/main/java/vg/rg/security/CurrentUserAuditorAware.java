package vg.rg.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import vg.unique.id.model.UniqueId;

import java.util.Objects;
import java.util.Optional;

/**
 * Supplies the current acting user's abstract {@link UniqueId} to Spring Data JPA auditing so that
 * {@code @CreatedBy}/{@code @LastModifiedBy} record author and last editor for auditing only. Returns
 * empty when there is no authenticated subject; the identity is never used for access control.
 */
@Component
public class CurrentUserAuditorAware implements AuditorAware<UniqueId> {

    private final AuthorityChecker authorityChecker;

    public CurrentUserAuditorAware(AuthorityChecker authorityChecker) {
        this.authorityChecker = Objects.requireNonNull(authorityChecker);
    }

    @Override
    public Optional<UniqueId> getCurrentAuditor() {
        return authorityChecker.currentUserUniqueId();
    }
}
