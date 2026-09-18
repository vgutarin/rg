package vg.rg.service.workspace.event;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.service.workspace.WorkspaceContentContributor;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's event registrations when the workspace is removed.
 *
 * <p><strong>Ordered first among the contributors.</strong> The foreign keys from a registration to its
 * event and participant are deliberately restricting (see the migration), so a registration must be
 * deleted before either endpoint or the delete fails with a constraint violation.
 * {@code WorkspaceService.delete} iterates contributors in {@code @Order}, so this runs first, then the
 * event ({@link #ORDER} + 1), then the participant and location. The whole chain is spelled out with
 * explicit orders so the dependency is a stated fact rather than an accident of bean registration.
 */
@Slf4j
@Order(EventRegistrationWorkspaceContentContributor.ORDER)
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class EventRegistrationWorkspaceContentContributor implements WorkspaceContentContributor {

    /**
     * First in the removal chain. A registration references an event and a participant, so it must be
     * removed before either; those in turn are removed before the location an event references.
     */
    static final int ORDER = 0;

    static final String RESOURCE_TYPE = "WORKSPACE_EVENT_REGISTRATION";

    private final WorkspaceEventRegistrationRepository repository;

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
        log.debug("Removed {} event registration(s) from workspace {}", removed, workspaceId);
    }
}
