package vg.rg.service.workspace;

import org.junit.jupiter.api.Test;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.security.WorkspaceScope;
import vg.unique.id.model.UniqueId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dispatch rules of {@link WorkspaceScopeResolverImpl}, and the property the whole design turns on:
 * resolution is <strong>depth-independent at constant cost</strong>.
 *
 * <p>The depth proof uses a synthetic provider standing in for a five-level chain
 * (workspace → group → event → comments → comment), because those types are out of scope for this
 * feature. That stand-in is not a shortcut — registering one provider is the entire cost of adding a
 * contained type, so it demonstrates the extension path as well as the depth property.
 */
class WorkspaceScopeResolverTest {

    private static final UniqueId WORKSPACE = new UniqueId(900L);
    private static final UniqueId OWNER = new UniqueId(7001L);
    private static final UniqueId STRANGER = new UniqueId(8002L);

    // ------------------------------------------------------------- depth-independence at constant cost

    @Test
    void fiveLevelChain_resolvesFromAnyIdentifierInIt() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        var chain = provider.buildChain(5);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        assertThat(chain).hasSize(5);
        for (var identifier : chain) {
            var scope = resolver.resolve(identifier, LocalPermissions.Location.UPDATE);

            assertThat(scope).as("identifier %s", identifier).isPresent();
            assertThat(scope.get().workspaceUniqueId()).isEqualTo(WORKSPACE);
            assertThat(scope.get().isOwnedBy(OWNER)).isTrue();
        }
    }

    @Test
    void fiveLevelChain_deniesANonOwnerAtEveryLevel() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        var chain = provider.buildChain(5);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        for (var identifier : chain) {
            var scope = resolver.resolve(identifier, LocalPermissions.Location.UPDATE);

            assertThat(scope).isPresent();
            assertThat(scope.get().isOwnedBy(STRANGER))
                    .as("non-owner must be denied at identifier %s", identifier)
                    .isFalse();
        }
    }

    @Test
    void everyLevelCostsExactlyOneLookup() {
        // The point of dispatching by permission rather than walking parents: a provider joins its full
        // path in one query, so the deepest level costs exactly what the shallowest does.
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        var chain = provider.buildChain(5);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        for (var identifier : chain) {
            provider.resetLookupCount();

            resolver.resolve(identifier, LocalPermissions.Location.UPDATE);

            assertThat(provider.lookupCount())
                    .as("lookups for identifier %s", identifier)
                    .isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------------------- dispatch rules

    @Test
    void resourceVerb_usesTheResourceLookup() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        provider.buildChain(1);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        resolver.resolve(new UniqueId(500_001L), LocalPermissions.Location.UPDATE);

        assertThat(provider.resourceLookups()).isEqualTo(1);
        assertThat(provider.containerLookups()).isZero();
    }

    @Test
    void createVerb_usesTheContainerLookup() {
        // Without this, dispatching on the resource part alone would look a workspace identifier up in
        // the location store, miss, and deny every location creation.
        var provider = new ChainProvider(LocalPermissions.Location.CREATE);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        resolver.resolve(WORKSPACE, LocalPermissions.Location.CREATE);

        assertThat(provider.containerLookups()).isEqualTo(1);
        assertThat(provider.resourceLookups()).isZero();
    }

    @Test
    void permissionClaimedByNoProvider_isDeniedWithoutAnyLookup() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        provider.buildChain(1);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        assertThat(resolver.resolve(new UniqueId(500_001L), LocalPermissions.Workspace.UPDATE))
                .isEmpty();
        assertThat(provider.lookupCount()).isZero();
    }

    @Test
    void permissionClaimedByTwoProviders_isDenied() {
        // Overlapping declarations would make resolution depend on bean ordering. Deny rather than pick.
        var first = new ChainProvider(LocalPermissions.Location.UPDATE);
        var second = new ChainProvider(LocalPermissions.Location.UPDATE);
        first.buildChain(1);
        second.buildChain(1);
        var resolver = new WorkspaceScopeResolverImpl(List.of(first, second));

        assertThat(resolver.resolve(new UniqueId(500_001L), LocalPermissions.Location.UPDATE))
                .isEmpty();
        assertThat(first.lookupCount()).isZero();
        assertThat(second.lookupCount()).isZero();
    }

    @Test
    void noProvidersAtAll_isDenied() {
        var resolver = new WorkspaceScopeResolverImpl(List.of());

        assertThat(resolver.resolve(WORKSPACE, LocalPermissions.Location.UPDATE)).isEmpty();
    }

    @Test
    void appWideOrUndeclaredPermission_isDeniedWithoutConsultingProviders() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        provider.buildChain(1);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        assertThat(resolver.resolve(WORKSPACE, Permissions.Workspace.OWNER)).isEmpty();
        assertThat(resolver.resolve(WORKSPACE, Permissions.Reports.READ)).isEmpty();
        assertThat(resolver.resolve(WORKSPACE, "locationn:update")).isEmpty();
        assertThat(resolver.resolve(WORKSPACE, null)).isEmpty();
        assertThat(provider.lookupCount()).isZero();
    }

    @Test
    void nullIdentifier_isDeniedWithoutConsultingProviders() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        provider.buildChain(1);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        assertThat(resolver.resolve(null, LocalPermissions.Location.UPDATE)).isEmpty();
        assertThat(provider.lookupCount()).isZero();
    }

    @Test
    void providerMiss_isDenied() {
        var provider = new ChainProvider(LocalPermissions.Location.UPDATE);
        provider.buildChain(2);
        var resolver = new WorkspaceScopeResolverImpl(List.of(provider));

        assertThat(resolver.resolve(new UniqueId(999_999L), LocalPermissions.Location.UPDATE))
                .isEmpty();
    }

    /**
     * A synthetic contained type. A real nested type resolves its whole chain with a single join; here a
     * map lookup stands in for that query, and the counters record how many are performed.
     */
    private static final class ChainProvider implements WorkspaceScopeProvider {

        private final String claimedPermission;
        private final Map<UniqueId, WorkspaceScope> chain = new HashMap<>();
        private int resourceLookups;
        private int containerLookups;

        private ChainProvider(String claimedPermission) {
            this.claimedPermission = claimedPermission;
        }

        List<UniqueId> buildChain(int levels) {
            var identifiers = new ArrayList<UniqueId>();
            for (var level = 1; level <= levels; level++) {
                var identifier = new UniqueId(500_000L + level);
                chain.put(identifier, new WorkspaceScope(WORKSPACE, OWNER));
                identifiers.add(identifier);
            }
            return identifiers;
        }

        void resetLookupCount() {
            resourceLookups = 0;
            containerLookups = 0;
        }

        int lookupCount() {
            return resourceLookups + containerLookups;
        }

        int resourceLookups() {
            return resourceLookups;
        }

        int containerLookups() {
            return containerLookups;
        }

        @Override
        public boolean supports(String permission) {
            return claimedPermission.equals(permission);
        }

        @Override
        public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
            resourceLookups++;
            return Optional.ofNullable(chain.get(resourceId));
        }

        @Override
        public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
            containerLookups++;
            return Optional.of(new WorkspaceScope(WORKSPACE, OWNER));
        }
    }
}
