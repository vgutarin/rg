package vg.rg.service.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Resolves a workspace addressed by its own identifier: a workspace scopes itself. This is what lets one
 * resource-scoped authority check govern both a workspace and everything inside it, with no special case
 * in the check itself.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceSelfScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceRepository repository;

    @Override
    public boolean supports(String permission) {
        return LocalPermissions.Workspace.contains(permission);
    }

    @Override
    public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
        return repository.findOwnerByUniqueId(resourceId.getLongValue())
                .map(row -> new WorkspaceScope(resourceId, row.getUniqueId()));
    }

    @Override
    public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
        // A workspace is a chain root, so it has no container to address. Creating a workspace has no
        // containing resource either, which is why it is guarded by the app-wide check instead.
        return Optional.empty();
    }
}
