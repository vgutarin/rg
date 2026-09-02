package vg.rg.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.rg.security.model.LocalPermissions;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dispatches to the {@link WorkspaceScopeProvider} that claims the declared permission's resource type,
 * then runs the container or resource lookup according to the permission's verb.
 *
 * <p>Fails closed at every step: an unrecognized permission, a permission no provider claims, a missing
 * row, or a row that does not join to a workspace all resolve to empty, which the authority check reads
 * as a denial. There is deliberately no fallback probe of other providers — that would silently accept a
 * mismatched permission/identifier pair, which is exactly the mistake this design makes visible.
 */
@Slf4j
@Component
class WorkspaceScopeResolverImpl implements WorkspaceScopeResolver {

    private final List<WorkspaceScopeProvider> providers;

    WorkspaceScopeResolverImpl(List<WorkspaceScopeProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission) {
        if (resourceId == null) {
            log.warn("Workspace scope not resolvable: no resource identifier for permission {}",
                    permission);
            return Optional.empty();
        }
        if (!LocalPermissions.isRecognized(permission)) {
            // Not a declared local permission, so no type can be derived from it. Includes an app-wide
            // permission handed to the resource-scoped check, and any typo.
            log.warn("Workspace scope not resolvable: {} is not a declared local permission", permission);
            return Optional.empty();
        }

        var provider = providerFor(permission);
        if (provider.isEmpty()) {
            log.warn("Workspace scope not resolvable: no provider claims permission {}", permission);
            return Optional.empty();
        }

        var addressesContainer = LocalPermissions.addressesContainer(permission);
        var scope = addressesContainer
                ? provider.get().findByContainer(resourceId)
                : provider.get().findByResource(resourceId);

        if (scope.isEmpty()) {
            // The resource genuinely does not exist, or the permission names a different type than the
            // identifier does. Both deny; the warning is what makes the second case visible in
            // operations rather than an unexplained denial.
            log.warn("Workspace scope not resolvable for resource {} using permission {} ({} lookup)",
                    resourceId, permission, addressesContainer ? "container" : "resource");
        }
        return scope;
    }

    private Optional<WorkspaceScopeProvider> providerFor(String permission) {
        WorkspaceScopeProvider claimed = null;
        for (var provider : providers) {
            if (!provider.supports(permission)) {
                continue;
            }
            if (claimed != null) {
                // Two providers claiming one permission means the declarations overlap, which would make
                // resolution depend on bean ordering. Deny rather than pick one.
                log.warn("Workspace scope not resolvable: permission {} is claimed by more than one "
                        + "provider", permission);
                return Optional.empty();
            }
            claimed = provider;
        }
        return Optional.ofNullable(claimed);
    }
}
