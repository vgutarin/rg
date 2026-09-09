package vg.rg.service.workspace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;
import vg.rg.mapper.workspace.WorkspaceMapper;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
class WorkspaceSelectionServiceImpl implements WorkspaceSelectionService {

    private final WorkspaceSelectionRepository selectionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final WorkspaceMapper mapper;
    private final AuthorityChecker authorityChecker;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Workspace.OWNER + "')")
    public WorkspaceModel activeWorkspace() {
        var user = currentUser();
        return selectedAndStillOwned(user)
                .map(mapper::toModel)
                .orElseGet(() -> {
                    // Absent, dangling, or no longer owned: fall back to the default and repair the
                    // pointer, so a stale selection can never surface as an error.
                    var fallback = workspaceService.ensureDefault();
                    store(user, fallback.getUniqueId());
                    return fallback;
                });
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Workspace.READ + "')")
    public void select(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        store(currentUser(), workspaceId);
    }

    /**
     * The selected workspace, but only when it still exists and the caller still owns it. Ownership is
     * re-checked on every read rather than trusted from when it was stored, because a workspace can be
     * removed or the permission revoked between sessions.
     */
    private Optional<WorkspaceEntity> selectedAndStillOwned(UniqueId user) {
        return selectionRepository.findById(user.getLongValue())
                .map(WorkspaceSelectionEntity::getWorkspaceUniqueId)
                .flatMap(workspaceRepository::findById)
                .filter(workspace -> user.equals(workspace.getOwnerUniqueId()));
    }

    private void store(UniqueId user, UniqueId workspaceId) {
        // One row per user, keyed by identity, so concurrent selections resolve last-write-wins on a
        // single row -- which is why every workspace screen states the active workspace rather than
        // assuming it.
        selectionRepository.save(WorkspaceSelectionEntity.builder()
                .userUniqueId(user.getLongValue())
                .workspaceUniqueId(workspaceId)
                .build());
    }

    private UniqueId currentUser() {
        return authorityChecker.currentUserUniqueId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
