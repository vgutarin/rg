package vg.rg.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.entity.WorkspaceEntity;
import vg.rg.entity.WorkspaceLocationEntity;
import vg.rg.repository.WorkspaceLocationRepository;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB-backed coverage of {@link WorkspaceScopeResolver} against MySQL: the real production join from a
 * location to its owning workspace, the container path used by {@code create}, and every way resolution
 * fails closed.
 *
 * <p>Depth-independence and the dispatch rules are proven in {@link WorkspaceScopeResolverTest}, which
 * can register a synthetic provider without colliding with the production ones.
 */
class WorkspaceScopeResolverFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(7001L);

    @Autowired
    private WorkspaceScopeResolver resolver;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceLocationRepository locationRepository;

    @Autowired
    private UniqueIdService uniqueIdService;

    private UniqueId workspaceId;

    @BeforeEach
    void setUp() {
        // Authenticate so JPA auditing has an auditor for the @CreatedBy columns.
        var principal = new AuthenticatedUserPrincipal(
                OWNER, "Test Owner",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        var workspace = workspaceRepository.saveWithNewUniqueId(
                WorkspaceEntity.builder().ownerUniqueId(OWNER).build(), uniqueIdService);
        workspaceId = new UniqueId(workspace.getUniqueId());
    }

    @AfterEach
    void cleanUp() {
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- the production resolution paths

    @Test
    void workspaceIdentifier_withWorkspacePermission_resolvesToItself() {
        var scope = resolver.resolve(workspaceId, LocalPermissions.Workspace.UPDATE);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(workspaceId);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
        assertThat(scope.get().isOwnedBy(OWNER)).isTrue();
    }

    @Test
    void locationIdentifier_withLocationPermission_resolvesThroughTheJoin() {
        var locationId = saveLocation();

        var scope = resolver.resolve(locationId, LocalPermissions.Location.UPDATE);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(workspaceId);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
    }

    @Test
    void workspaceIdentifier_withLocationCreatePermission_resolvesViaTheContainerLookup() {
        // Creation has no resource identifier yet, so the create verb addresses the container.
        var scope = resolver.resolve(workspaceId, LocalPermissions.Location.CREATE);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(workspaceId);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
    }

    @Test
    void locationOfAnotherOwnersWorkspace_resolvesToThatOtherOwner() {
        var stranger = new UniqueId(7002L);
        var otherWorkspace = workspaceRepository.saveWithNewUniqueId(
                WorkspaceEntity.builder().ownerUniqueId(stranger).build(), uniqueIdService);
        var foreignLocationId = saveLocationIn(new UniqueId(otherWorkspace.getUniqueId()));

        var scope = resolver.resolve(foreignLocationId, LocalPermissions.Location.UPDATE);

        assertThat(scope).isPresent();
        assertThat(scope.get().isOwnedBy(stranger)).isTrue();
        assertThat(scope.get().isOwnedBy(OWNER)).isFalse();
    }

    // ------------------------------------------------------------------------------ failing closed

    @Test
    void workspaceIdentifier_withNonCreateLocationPermission_isDenied() {
        // Permission and identifier must describe the same resource: a workspace id with a
        // resource-addressed location permission looks for a location with that id and finds none.
        assertThat(resolver.resolve(workspaceId, LocalPermissions.Location.UPDATE)).isEmpty();
        assertThat(resolver.resolve(workspaceId, LocalPermissions.Location.DELETE)).isEmpty();
        assertThat(resolver.resolve(workspaceId, LocalPermissions.Location.READ)).isEmpty();
    }

    @Test
    void locationIdentifier_withWorkspacePermission_isDenied() {
        var locationId = saveLocation();

        assertThat(resolver.resolve(locationId, LocalPermissions.Workspace.UPDATE)).isEmpty();
    }

    @Test
    void unknownIdentifier_isDenied() {
        assertThat(resolver.resolve(new UniqueId(424242L), LocalPermissions.Location.UPDATE)).isEmpty();
        assertThat(resolver.resolve(new UniqueId(424242L), LocalPermissions.Workspace.UPDATE)).isEmpty();
        assertThat(resolver.resolve(new UniqueId(424242L), LocalPermissions.Location.CREATE)).isEmpty();
    }

    @Test
    void nullIdentifier_isDenied() {
        assertThat(resolver.resolve(null, LocalPermissions.Location.UPDATE)).isEmpty();
    }

    @Test
    void appWidePermission_isDenied() {
        assertThat(resolver.resolve(workspaceId, Permissions.Workspace.OWNER)).isEmpty();
        assertThat(resolver.resolve(workspaceId, Permissions.Reports.READ)).isEmpty();
    }

    @Test
    void undeclaredPermission_isDenied() {
        assertThat(resolver.resolve(workspaceId, "location:frobnicate")).isEmpty();
        // A near-miss must claim nothing: dispatch is set membership, not prefix parsing.
        assertThat(resolver.resolve(workspaceId, "locationn:update")).isEmpty();
        assertThat(resolver.resolve(workspaceId, null)).isEmpty();
    }

    @Test
    void workspaceCreatePermission_resolvesLikeAnyOtherWorkspacePermission() {
        // workspace:create has no call site -- creating a workspace has no containing resource to
        // address, so it is guarded by the app-wide check. It is therefore not container-addressed
        // either, and gets no special case here: it resolves through the ordinary resource path like
        // every other workspace permission. That keeps the owner allowed in their own workspace, rather
        // than adding a lone rule that denies them something.
        var scope = resolver.resolve(workspaceId, LocalPermissions.Workspace.CREATE);

        assertThat(scope).isPresent();
        assertThat(scope.get().isOwnedBy(OWNER)).isTrue();
    }

    private UniqueId saveLocation() {
        return saveLocationIn(workspaceId);
    }

    private UniqueId saveLocationIn(UniqueId targetWorkspaceId) {
        var saved = locationRepository.saveWithNewUniqueId(
                WorkspaceLocationEntity.builder()
                        .workspaceUniqueId(targetWorkspaceId)
                        .name("Somewhere")
                        .latitude(BigDecimal.valueOf(50.0))
                        .longitude(BigDecimal.valueOf(30.0))
                        .build(),
                uniqueIdService);
        return new UniqueId(saved.getUniqueId());
    }
}
