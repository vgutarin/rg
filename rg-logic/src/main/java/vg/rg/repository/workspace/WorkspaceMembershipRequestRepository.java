package vg.rg.repository.workspace;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.workspace.WorkspaceMembershipRequestEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

/** Membership requests are scoped by workspace and never queried as a global people directory. */
public interface WorkspaceMembershipRequestRepository
        extends UniqueIdJpaRepository<WorkspaceMembershipRequestEntity> {

    Optional<WorkspaceMembershipRequestEntity> findByWorkspaceUniqueIdAndPendingUserUniqueId(
            UniqueId workspaceUniqueId, UniqueId pendingUserUniqueId);

    List<WorkspaceMembershipRequestEntity> findByWorkspaceUniqueIdOrderByCreatedAtAsc(UniqueId workspaceUniqueId);

    long countByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    void deleteByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    @Modifying
    @Query("""
            update WorkspaceMembershipRequestEntity r set r.originEventUniqueId = null
            where r.originEventUniqueId = :eventUniqueId
            """)
    void clearOriginEventUniqueId(@Param("eventUniqueId") UniqueId eventUniqueId);

}
