package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.service.workspace.WorkspaceContentContributor;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class EventWorkspaceContentContributor implements WorkspaceContentContributor {

    private final WorkspaceEventRepository repository;

    @Override
    public String resourceType() {
        return "WORKSPACE_EVENT";
    }

    @Override
    public void deleteAllInWorkspace(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var removed = repository.countByWorkspaceUniqueId(workspaceId);
        repository.deleteByWorkspaceUniqueId(workspaceId);
        log.debug("Removed {} event(s) from workspace {}", removed, workspaceId);
    }
}
