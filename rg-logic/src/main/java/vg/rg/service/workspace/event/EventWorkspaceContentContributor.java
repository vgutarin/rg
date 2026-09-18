package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.service.workspace.WorkspaceContentContributor;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's events when the workspace is removed.
 *
 * <p>Ordered after {@link EventRegistrationWorkspaceContentContributor} (its registrations must go
 * first) and before the location contributor, since an event's FK to its location is restricting.
 */
@Slf4j
@Order(EventRegistrationWorkspaceContentContributor.ORDER + 1)
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
