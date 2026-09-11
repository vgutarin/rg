package vg.rg.repository.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.workspace.WorkspaceEventEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/** Workspace-scoped event persistence. */
public interface WorkspaceEventRepository extends UniqueIdJpaRepository<WorkspaceEventEntity> {

    Page<WorkspaceEventEntity> findByWorkspaceUniqueId(UniqueId workspaceUniqueId, Pageable pageable);

    Page<WorkspaceEventEntity> findByWorkspaceUniqueIdAndTitleContainingIgnoreCase(
            UniqueId workspaceUniqueId, String title, Pageable pageable);

    long countByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    void deleteByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    @Query("""
            select e.workspaceUniqueId as workspaceUniqueId, w.ownerUniqueId as ownerUniqueId
            from WorkspaceEventEntity e
              join WorkspaceEntity w on w.uniqueId = e.workspaceUniqueId
            where e.uniqueId = :uniqueId
            """)
    Optional<WorkspaceScopeRow> findWorkspaceScopeByUniqueId(@Param("uniqueId") Long uniqueId);
}
