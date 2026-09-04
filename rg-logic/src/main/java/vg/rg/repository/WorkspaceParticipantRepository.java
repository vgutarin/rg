package vg.rg.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.WorkspaceParticipantEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

/**
 * Every read takes a workspace, so an unscoped query cannot be written — a property of the signatures
 * rather than a convention to remember, as on {@link WorkspaceLocationRepository}.
 *
 * <p>There is deliberately nothing here that searches, sorts, or filters on the descriptor. It is
 * encrypted with a fresh IV per write, so the database cannot compare two values, and no index could
 * help; the service reads a workspace's rows and works over them in memory instead, which is what the
 * roster count bound exists to keep finite.
 */
public interface WorkspaceParticipantRepository extends UniqueIdJpaRepository<WorkspaceParticipantEntity> {

    /**
     * The whole roster of one workspace, and the <strong>only</strong> read.
     *
     * <p>There is deliberately no {@code Pageable} overload. Both things the service does with a roster
     * — ordering by label and detecting a duplicate phone number — have to open every descriptor, so a
     * database page would be the wrong subset rather than a smaller amount of work. Paging happens after
     * ordering, in the service. Bounded by {@code rg.workspace.participants-max-per-workspace}.
     */
    List<WorkspaceParticipantEntity> findByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    long countByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    void deleteByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    /**
     * The workspace-scope lookup for a participant addressed by its own identifier: one query joining
     * through to the owning workspace, so an authority check costs a single round trip. See
     * {@link WorkspaceLocationRepository#findWorkspaceScopeByUniqueId(Long)} for why this is an ad-hoc
     * join feeding an interface projection.
     */
    @Query("""
            select p.workspaceUniqueId as workspaceUniqueId, w.ownerUniqueId as ownerUniqueId
            from WorkspaceParticipantEntity p
              join WorkspaceEntity w on w.uniqueId = p.workspaceUniqueId
            where p.uniqueId = :uniqueId
            """)
    Optional<WorkspaceScopeRow> findWorkspaceScopeByUniqueId(@Param("uniqueId") Long uniqueId);
}
