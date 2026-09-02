package vg.rg.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

import java.util.Objects;
import java.util.Optional;

@Component
public final class AuthorityChecker {

    private final WorkspaceScopeResolver workspaceScopeResolver;

    public AuthorityChecker(WorkspaceScopeResolver workspaceScopeResolver) {
        this.workspaceScopeResolver = Objects.requireNonNull(
                workspaceScopeResolver, "workspaceScopeResolver");
    }

    /**
     * The application-wide check: whether the caller holds a permission that means something without
     * naming a resource.
     *
     * <p>A purely <em>local</em> permission is rejected here, because it is meaningless without a
     * resource — use {@link #hasAuthority(UniqueId, String)} for those. The location capabilities are
     * the documented exception while the global collection is being retired.
     */
    public boolean hasAuthority(String permission) {
        if (!Permissions.isRecognized(permission)) {
            return false;
        }
        return currentPrincipal()
                .map(principal -> principal.userUniqueId() != null
                        && principal.permissions().contains(permission))
                .orElse(false);
    }

    /**
     * The workspace-scoped check, and the only one: whether the caller may act on the given resource.
     *
     * <p>{@code resourceId} names the resource being acted upon — for a location update, the location's
     * own identifier, not its workspace. The one exception is a {@code create} permission, where no
     * resource exists yet and the identifier names the container instead. Either way the containing
     * workspace is resolved here, never by the caller.
     *
     * <p>Grants access when all of the following hold:
     * <ol>
     *   <li>{@code permission} is a declared local permission;</li>
     *   <li>a principal is authenticated with an abstract identity;</li>
     *   <li>the principal holds the application-wide workspace permission, which gates the layer;</li>
     *   <li>the resource resolves to a workspace the principal <strong>owns</strong>.</li>
     * </ol>
     *
     * <p><strong>Ownership overrides the permission value.</strong> Once the caller is found to own the
     * resolved workspace, whether they <em>hold</em> the declared permission is not consulted: a
     * workspace owner has complete authority over their workspace and everything inside it, at any
     * depth. The permission is still validated as declared — that catches call-site typos and mis-scoped
     * values — and it carries the resource type the resolver dispatches on, so it is load-bearing rather
     * than decorative. When granular non-owner access arrives, the held-check becomes enforced for
     * non-owners here, and no call site changes.
     *
     * <p>Fails closed. Returns {@code false} — never throws for a denial, and never reveals whether the
     * resource exists — when any condition fails, including an unresolvable identifier. There is no
     * fallback to a permission-only decision.
     */
    public boolean hasAuthority(UniqueId resourceId, String permission) {
        if (!LocalPermissions.isRecognized(permission)) {
            return false;
        }
        var principal = currentPrincipal().orElse(null);
        if (principal == null || principal.userUniqueId() == null) {
            return false;
        }
        if (!principal.permissions().contains(Permissions.Workspace.OWNER)) {
            return false;
        }
        return workspaceScopeResolver.resolve(resourceId, permission)
                .map(scope -> scope.isOwnedBy(principal.userUniqueId()))
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
