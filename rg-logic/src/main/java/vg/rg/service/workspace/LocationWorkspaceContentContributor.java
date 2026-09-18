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
 * <p>Ordered last: an event carries a restricting FK to its location, so every event must be gone
 * before its locations. The event contributor runs at order 1, this at order 3.
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LocationWorkspaceContentContributor implements WorkspaceContentContributor {

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
