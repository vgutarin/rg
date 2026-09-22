package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.service.resource.ResourcePublicationService;
import vg.rg.service.workspace.LocationWorkspaceContentContributor;
import vg.rg.service.workspace.WorkspaceContentContributor;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's events when the workspace is removed.
 *
 * <p>Ordered after {@link EventRegistrationWorkspaceContentContributor} (its registrations must go
 * first) and before its location, which it references through a restrictive foreign key.
 */
@Slf4j
@Order(EventWorkspaceContentContributor.ORDER)
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class EventWorkspaceContentContributor implements WorkspaceContentContributor {

    public static final int ORDER = LocationWorkspaceContentContributor.ORDER - 1;

    private final WorkspaceEventRepository repository;
    private final ResourcePublicationService publicationService;

    @Override
    public String resourceType() {
        return "WORKSPACE_EVENT";
    }

    @Override
    public void deleteAllInWorkspace(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var removed = repository.countByWorkspaceUniqueId(workspaceId);
        var eventIds = repository.findUniqueIdsByWorkspaceUniqueId(workspaceId).stream().map(UniqueId::new).toList();
        publicationService.deleteAll(eventIds);
        repository.deleteByWorkspaceUniqueId(workspaceId);
        log.debug("Removed {} event(s) from workspace {}", removed, workspaceId);
    }
}
