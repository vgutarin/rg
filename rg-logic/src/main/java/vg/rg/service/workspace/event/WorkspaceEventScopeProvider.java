package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.workspace.WorkspaceScopeProvider;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceEventScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    public boolean supports(String permission) {
        return LocalPermissions.WorkspaceEvent.contains(permission);
    }

    @Override
    public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
        return eventRepository.findWorkspaceScopeByUniqueId(resourceId.getLongValue())
                .map(row -> new WorkspaceScope(row.getWorkspaceUniqueId(), row.getOwnerUniqueId()));
    }

    @Override
    public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
        return workspaceRepository.findOwnerByUniqueId(containerId.getLongValue())
                .map(row -> new WorkspaceScope(containerId, row.getUniqueId()));
    }
}
