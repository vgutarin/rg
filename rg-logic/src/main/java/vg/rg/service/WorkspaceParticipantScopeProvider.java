package vg.rg.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.repository.WorkspaceParticipantRepository;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.WorkspaceScope;
import vg.rg.security.WorkspaceScopeProvider;
import vg.rg.security.model.LocalPermissions;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Resolves a participant to its workspace, exactly as {@link LocationScopeProvider} does for a location.
 *
 * <p>Registering this and a {@link WorkspaceContentContributor} is the entire cost of adding a contained
 * type: no access rule changed, no call site changed, and no new gate. That is the seam working as
 * designed rather than a coincidence.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceParticipantScopeProvider implements WorkspaceScopeProvider {

    private final WorkspaceParticipantRepository participantRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    public boolean supports(String permission) {
        return LocalPermissions.WorkspaceParticipant.contains(permission);
    }

    @Override
    public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
        return participantRepository.findWorkspaceScopeByUniqueId(resourceId.getLongValue())
                .map(row -> new WorkspaceScope(row.getWorkspaceUniqueId(), row.getOwnerUniqueId()));
    }

    @Override
    public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
        return workspaceRepository.findOwnerByUniqueId(containerId.getLongValue())
                .map(row -> new WorkspaceScope(containerId, row.getUniqueId()));
    }
}
