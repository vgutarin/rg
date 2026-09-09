package vg.rg.service.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/**
 * Removes a workspace's participants when the workspace is removed.
 *
 * <p><strong>Why this exists rather than {@code ON DELETE CASCADE}</strong>, which would be one line of
 * Liquibase: the foreign key is deliberately restricting, so a contained type without a contributor makes
 * workspace removal <em>fail</em> instead of quietly taking rows with it. That turns forgetting one into
 * a test failure. A database cascade would also be invisible to JPA — already-loaded entities would go
 * stale and {@code @Version} would be bypassed — would fire no auditing, could never refuse, and could
 * never reach anything outside this one table, which is where a future contained type will need to
 * unbind an invite or drop a stored file.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ParticipantWorkspaceContentContributor implements WorkspaceContentContributor {

    static final String RESOURCE_TYPE = "WORKSPACE_PARTICIPANT";

    private final WorkspaceParticipantRepository repository;

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
        log.debug("Removed {} participant(s) from workspace {}", removed, workspaceId);
    }
}
