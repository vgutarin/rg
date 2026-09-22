package vg.rg.service.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceMembershipRequestRepository;
import vg.rg.service.workspace.event.EventWorkspaceContentContributor;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/** Removes requests before their optional event origins are removed. */
@Slf4j
@Order(MembershipRequestWorkspaceContentContributor.ORDER)
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MembershipRequestWorkspaceContentContributor implements WorkspaceContentContributor {

    // A request has workspace and optional event parents; the event has the lower order.
    static final int ORDER = EventWorkspaceContentContributor.ORDER - 1;

    private final WorkspaceMembershipRequestRepository repository;

    @Override
    public String resourceType() {
        return "WORKSPACE_MEMBERSHIP_REQUEST";
    }

    @Override
    public void deleteAllInWorkspace(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        var removed = repository.countByWorkspaceUniqueId(workspaceId);
        repository.deleteByWorkspaceUniqueId(workspaceId);
        log.debug("Removed {} membership request(s) from workspace {}", removed, workspaceId);
    }
}
