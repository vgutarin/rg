package vg.rg.service.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's locations when the workspace is removed.
 *
 * <p>A direct workspace child, ordered immediately before its workspace. Events run earlier because
 * an event's FK to its location is restricting.
 */
@Slf4j
@Order(LocationWorkspaceContentContributor.ORDER)
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class LocationWorkspaceContentContributor implements WorkspaceContentContributor {

    public static final int ORDER = DIRECT_WORKSPACE_CHILD_ORDER;
    static final String RESOURCE_TYPE = "LOCATION";

    private final WorkspaceLocationRepository repository;

    @Override
    public String resourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public void deleteAllInWorkspace(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        // Scoped by the mandatory column, so this cannot reach another workspace's rows.
        var removed = repository.countByWorkspaceUniqueId(workspaceId);
        repository.deleteByWorkspaceUniqueId(workspaceId);
        log.debug("Removed {} location(s) from workspace {}", removed, workspaceId);
    }
}
