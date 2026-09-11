package vg.rg.service.workspace.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.repository.UniqueIdRow;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceScopeRow;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceEventScopeProviderTest {

    private static final UniqueId WORKSPACE = nextUniqueId();
    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId EVENT = nextUniqueId();

    @Mock WorkspaceEventRepository eventRepository;
    @Mock WorkspaceRepository workspaceRepository;

    private WorkspaceEventScopeProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WorkspaceEventScopeProvider(eventRepository, workspaceRepository);
    }

    @Test
    void supports_claimsOnlyEventPermissions() {
        assertThat(provider.supports(LocalPermissions.WorkspaceEvent.READ)).isTrue();
        assertThat(provider.supports(LocalPermissions.WorkspaceEvent.LIST)).isTrue();
        assertThat(provider.supports(LocalPermissions.WorkspaceEvent.CREATE)).isTrue();
        assertThat(provider.supports(LocalPermissions.WorkspaceEvent.UPDATE)).isTrue();
        assertThat(provider.supports(LocalPermissions.WorkspaceEvent.DELETE)).isTrue();

        assertThat(provider.supports(LocalPermissions.WorkspaceParticipant.UPDATE)).isFalse();
        assertThat(provider.supports(Permissions.Workspace.OWNER)).isFalse();
        assertThat(provider.supports("workspace-events:update")).isFalse();
        assertThat(provider.supports(null)).isFalse();
    }

    @Test
    void findByResource_knownEvent_returnsItsWorkspaceAndOwner() {
        when(eventRepository.findWorkspaceScopeByUniqueId(EVENT.getLongValue()))
                .thenReturn(Optional.of(scopeRow()));

        var scope = provider.findByResource(EVENT);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(WORKSPACE);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
        assertThat(scope.get().isOwnedBy(OWNER)).isTrue();
    }

    @Test
    void findByResource_unknownEvent_isEmpty() {
        when(eventRepository.findWorkspaceScopeByUniqueId(EVENT.getLongValue())).thenReturn(Optional.empty());

        assertThat(provider.findByResource(EVENT)).isEmpty();
    }

    @Test
    void findByContainer_knownWorkspace_returnsItAndItsOwner() {
        when(workspaceRepository.findOwnerByUniqueId(WORKSPACE.getLongValue()))
                .thenReturn(Optional.of(ownerRow()));

        var scope = provider.findByContainer(WORKSPACE);

        assertThat(scope).isPresent();
        assertThat(scope.get().workspaceUniqueId()).isEqualTo(WORKSPACE);
        assertThat(scope.get().ownerUniqueId()).isEqualTo(OWNER);
    }

    @Test
    void findByContainer_unknownWorkspace_isEmpty() {
        when(workspaceRepository.findOwnerByUniqueId(WORKSPACE.getLongValue())).thenReturn(Optional.empty());

        assertThat(provider.findByContainer(WORKSPACE)).isEmpty();
    }

    private static UniqueIdRow ownerRow() {
        return () -> OWNER;
    }

    private static WorkspaceScopeRow scopeRow() {
        return new WorkspaceScopeRow() {
            @Override
            public UniqueId getWorkspaceUniqueId() {
                return WORKSPACE;
            }

            @Override
            public UniqueId getOwnerUniqueId() {
                return OWNER;
            }
        };
    }
}
