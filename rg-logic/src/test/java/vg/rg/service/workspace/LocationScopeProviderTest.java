package vg.rg.service.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceScopeRow;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationScopeProviderTest {

    private static final UniqueId WORKSPACE = new UniqueId(5001L);
    private static final UniqueId OWNER = new UniqueId(3001L);
    private static final UniqueId LOCATION = new UniqueId(6001L);

    @Mock
    private WorkspaceLocationRepository locationRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;

    private LocationScopeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocationScopeProvider(locationRepository, workspaceRepository);
    }

    @Test
    void supports_claimsOnlyLocationPermissions() {
        assertThat(provider.supports(LocalPermissions.Location.READ)).isTrue();
        assertThat(provider.supports(LocalPermissions.Location.CREATE)).isTrue();
        assertThat(provider.supports(LocalPermissions.Location.UPDATE)).isTrue();
        assertThat(provider.supports(LocalPermissions.Location.DELETE)).isTrue();

        assertThat(provider.supports(LocalPermissions.Workspace.UPDATE)).isFalse();
        assertThat(provider.supports(Permissions.Workspace.OWNER)).isFalse();
        // A near-miss must claim nothing: this is set membership, not prefix parsing.
        assertThat(provider.supports("locationn:update")).isFalse();
        assertThat(provider.supports(null)).isFalse();
    }

    @Test
    void findByResource_knownLocation_returnsItsWorkspaceAndOwner() {
        when(locationRepository.findWorkspaceScopeByUniqueId(LOCATION.getLongValue()))
                .thenReturn(Optional.of(row(WORKSPACE, OWNER)));

        var scope = provider.findByResource(LOCATION);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(WORKSPACE);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
        assertThat(scope.get().isOwnedBy(OWNER)).isTrue();
    }

    @Test
    void findByResource_unknownLocation_isEmpty() {
        when(locationRepository.findWorkspaceScopeByUniqueId(LOCATION.getLongValue()))
                .thenReturn(Optional.empty());

        assertThat(provider.findByResource(LOCATION)).isEmpty();
    }

    @Test
    void findByContainer_knownWorkspace_returnsIt() {
        // The create path: no location exists yet, so the identifier names the container.
        when(workspaceRepository.findOwnerByUniqueId(WORKSPACE.getLongValue()))
                .thenReturn(Optional.of(ownerRow(OWNER)));

        var scope = provider.findByContainer(WORKSPACE);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(WORKSPACE);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
    }

    @Test
    void findByContainer_unknownWorkspace_isEmpty() {
        when(workspaceRepository.findOwnerByUniqueId(WORKSPACE.getLongValue()))
                .thenReturn(Optional.empty());

        assertThat(provider.findByContainer(WORKSPACE)).isEmpty();
    }

    /** A single-identifier projection, as the workspace-owner lookup returns. */
    private static vg.rg.repository.UniqueIdRow ownerRow(UniqueId owner) {
        return () -> owner;
    }

    private static WorkspaceScopeRow row(UniqueId workspace, UniqueId owner) {
        return new WorkspaceScopeRow() {
            @Override
            public UniqueId getWorkspaceUniqueId() {
                return workspace;
            }

            @Override
            public UniqueId getOwnerUniqueId() {
                return owner;
            }
        };
    }
}
