package vg.rg.service;

import org.springframework.stereotype.Component;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.WorkspaceScope;
import vg.rg.security.WorkspaceScopeProvider;
import vg.rg.security.model.LocalPermissions;
import vg.unique.id.model.UniqueId;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a workspace addressed by its own identifier: a workspace scopes itself. This is what lets one
 * resource-scoped authority check govern both a workspace and everything inside it, with no special case
 * in the check itself.
 */
@Component
class WorkspaceSelfScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceRepository repository;

    WorkspaceSelfScopeProvider(WorkspaceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

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
