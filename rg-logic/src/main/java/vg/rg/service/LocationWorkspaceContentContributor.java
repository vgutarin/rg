package vg.rg.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.rg.repository.WorkspaceLocationRepository;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's locations when the workspace is removed. The only contributor this feature
 * ships; a future contained type adds one of these and changes nothing else about removal.
 */
@Slf4j
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
