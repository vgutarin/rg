package vg.rg.service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.service.workspace.WorkspaceScopeResolver;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthorityCheckerTest {

    private static final UniqueId USER = new UniqueId(1001L);
    private static final UniqueId OTHER_USER = new UniqueId(2002L);
    private static final UniqueId RESOURCE = new UniqueId(3003L);

    @Mock
    private WorkspaceScopeResolver workspaceScopeResolver;

    private AuthorityChecker checker;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthorityChecker checker() {
        if (checker == null) {
            checker = new AuthorityChecker(workspaceScopeResolver);
        }
        return checker;
    }

    // ---------------------------------------------------------------- flat, app-wide overload

    @Test
    void hasAuthority_grantedPermission_returnsTrue() {
        authenticate(USER, Set.of(Permissions.Reports.READ, Permissions.Request.SUBMIT));

        assertThat(checker().hasAuthority(Permissions.Reports.READ)).isTrue();
    }

    @Test
    void hasAuthority_missingPermission_returnsFalse() {
        authenticate(USER, Set.of(Permissions.Request.SUBMIT));

        assertThat(checker().hasAuthority(Permissions.Reports.READ)).isFalse();
    }

    @Test
    void hasAuthority_workspaceOwnerGate_returnsTrue() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER));

        assertThat(checker().hasAuthority(Permissions.Workspace.OWNER)).isTrue();
    }

    @Test
    void hasAuthority_missingAuthentication_returnsFalse() {
        assertThat(checker().hasAuthority(Permissions.Reports.READ)).isFalse();
    }

    @Test
    void hasAuthority_unrecognizedPermission_returnsFalse() {
        authenticate(USER, Set.of("unknown:view"));

        assertThat(checker().hasAuthority("unknown:view")).isFalse();
    }

    @Test
    void hasAuthority_purelyLocalPermissionOnFlatOverload_returnsFalse() {
        // A local permission is meaningless without a resource. Even holding it must not pass the flat
        // check, or a caller could sidestep workspace scoping entirely.
        authenticate(USER, Set.of(LocalPermissions.Workspace.UPDATE, Permissions.Workspace.OWNER));

        assertThat(checker().hasAuthority(LocalPermissions.Workspace.UPDATE)).isFalse();
        assertThat(checker().hasAuthority(LocalPermissions.Workspace.DELETE)).isFalse();
    }

    // -------------------------------------------------- resource-scoped overload: the four conditions

    @Test
    void hasAuthorityOnResource_allConditionsMet_returnsTrue() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER, LocalPermissions.Location.UPDATE));
        resolvesTo(USER);

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isTrue();
    }

    @Test
    void hasAuthorityOnResource_undeclaredPermission_returnsFalse() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER));

        assertThat(checker().hasAuthority(RESOURCE, "location:frobnicate")).isFalse();
        assertThat(checker().hasAuthority(RESOURCE, null)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_appWidePermission_returnsFalse() {
        // The resource-scoped check accepts only local permissions. Handing it the app-wide gate must
        // deny rather than resolve, even for a workspace owner.
        authenticate(USER, Set.of(Permissions.Workspace.OWNER));

        assertThat(checker().hasAuthority(RESOURCE, Permissions.Workspace.OWNER)).isFalse();
        assertThat(checker().hasAuthority(RESOURCE, Permissions.Reports.READ)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_missingAuthentication_returnsFalse() {
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_missingWorkspaceOwnerGate_returnsFalse() {
        // Holding the capability but not the layer gate denies: the feature is opt-in by permission.
        authenticate(USER, Set.of(LocalPermissions.Location.UPDATE));
        resolvesTo(USER);

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_notTheWorkspaceOwner_returnsFalse() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER, LocalPermissions.Location.UPDATE));
        resolvesTo(OTHER_USER);

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_unresolvableResource_returnsFalse() {
        // Fails closed: an unresolvable identifier is never treated as unscoped.
        authenticate(USER, Set.of(Permissions.Workspace.OWNER, LocalPermissions.Location.UPDATE));
        lenient().when(workspaceScopeResolver.resolve(any(), any())).thenReturn(Optional.empty());

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_nullResourceId_returnsFalse() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER, LocalPermissions.Location.UPDATE));
        lenient().when(workspaceScopeResolver.resolve(any(), any())).thenReturn(Optional.empty());

        assertThat(checker().hasAuthority(null, LocalPermissions.Location.UPDATE)).isFalse();
    }

    // ------------------------------------------------------ ownership overrides the permission value

    @Test
    void hasAuthorityOnResource_ownerHoldingNoLocalCapabilities_isAllowed() {
        // The property the whole layer rests on: owning the workspace grants complete authority over its
        // contents, so the owner needs no per-capability grant inside their own workspace.
        authenticate(USER, Set.of(Permissions.Workspace.OWNER));
        resolvesTo(USER);

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.READ)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.CREATE)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.DELETE)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Workspace.UPDATE)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Workspace.DELETE)).isTrue();
    }

    @Test
    void hasAuthorityOnResource_nonOwnerHoldingEveryCapability_isDenied() {
        // The mirror image: capabilities without ownership grant nothing, because workspaces are
        // single-owner and there is no non-owner access path yet.
        authenticate(USER, Set.of(Permissions.Workspace.OWNER, LocalPermissions.Location.READ,
                LocalPermissions.Location.CREATE, LocalPermissions.Location.UPDATE,
                LocalPermissions.Location.DELETE));
        resolvesTo(OTHER_USER);

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.READ)).isFalse();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.DELETE)).isFalse();
    }

    @Test
    void hasAuthorityOnResource_permissionIsPassedToTheResolverAsTheTypeHint() {
        authenticate(USER, Set.of(Permissions.Workspace.OWNER));
        lenient().when(workspaceScopeResolver.resolve(RESOURCE, LocalPermissions.Location.UPDATE))
                .thenReturn(Optional.of(new WorkspaceScope(new UniqueId(9L), USER)));
        // A different permission is a different type, so it must not resolve through the same stub.
        lenient().when(workspaceScopeResolver.resolve(RESOURCE, LocalPermissions.Workspace.UPDATE))
                .thenReturn(Optional.empty());

        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Location.UPDATE)).isTrue();
        assertThat(checker().hasAuthority(RESOURCE, LocalPermissions.Workspace.UPDATE)).isFalse();
    }

    // ------------------------------------------------------------------------------- principal access

    @Test
    void currentUserUniqueId_authenticated_returnsAbstractIdentity() {
        authenticate(USER, Set.of(Permissions.Reports.READ));

        assertThat(checker().currentUserUniqueId()).contains(USER);
    }

    @Test
    void currentUserUniqueId_missingAuthentication_returnsEmpty() {
        assertThat(checker().currentUserUniqueId()).isEmpty();
    }

    @Test
    void currentAuthenticationFlow_authenticated_returnsFlow() {
        authenticate(USER, Set.of(Permissions.Reports.READ));

        assertThat(checker().currentAuthenticationFlow()).contains(AuthenticationFlow.TELEGRAM);
    }

    private void resolvesTo(UniqueId owner) {
        lenient().when(workspaceScopeResolver.resolve(any(), any()))
                .thenReturn(Optional.of(new WorkspaceScope(new UniqueId(9009L), owner)));
    }

    private void authenticate(UniqueId user, Set<String> permissions) {
        var principal = AuthenticatedUserPrincipal.builder()
                .userUniqueId(user)
                .permissions(permissions)
                .authenticationFlow(AuthenticationFlow.TELEGRAM)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
