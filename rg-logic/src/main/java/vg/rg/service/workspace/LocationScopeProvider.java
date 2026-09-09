package vg.rg.service.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Resolves a workspace-scoped location to its workspace. The only contained-type provider this feature
 * ships; a future nested type registers one of these and changes nothing else.
 *
 * <p>{@link #findByResource} is one query that joins the location through to its workspace, so the cost
 * does not grow with how many levels a type sits below the workspace. {@link #findByContainer} handles
 * the {@code location:create} case, where the identifier names the workspace the new location will go
 * into because no location identifier exists yet.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LocationScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceLocationRepository locationRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    public boolean supports(String permission) {
        return LocalPermissions.Location.contains(permission);
    }

    @Override
    public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
        return locationRepository.findWorkspaceScopeByUniqueId(resourceId.getLongValue())
                .map(row -> new WorkspaceScope(row.getWorkspaceUniqueId(), row.getOwnerUniqueId()));
    }

    @Override
    public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
        return workspaceRepository.findOwnerByUniqueId(containerId.getLongValue())
                .map(row -> new WorkspaceScope(containerId, row.getUniqueId()));
    }
}
