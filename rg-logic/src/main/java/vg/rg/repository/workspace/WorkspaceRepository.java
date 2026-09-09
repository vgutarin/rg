package vg.rg.repository.workspace;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.repository.UniqueIdRow;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends UniqueIdJpaRepository<WorkspaceEntity> {

    List<WorkspaceEntity> findByOwnerUniqueIdOrderByCreatedAtAsc(UniqueId ownerUniqueId);

    /**
     * The owner's default workspace. At most one can exist per owner — the unique index on
     * {@code default_for_owner} enforces it.
     */
    Optional<WorkspaceEntity> findByDefaultForOwner(UniqueId ownerUniqueId);

    long countByOwnerUniqueId(UniqueId ownerUniqueId);

    /**
     * The owner of a workspace addressed by its own identifier — the workspace-scope lookup used when the
     * declared permission is workspace-scoped. Projects the owner alone rather than loading the entity,
     * because the authority check needs nothing else.
     *
     * <p>Wrapped in {@link UniqueIdRow} rather than returned as a bare {@code UniqueId}; see that type for
     * why the identifier must not sit in the return position.
     */
    @Query("""
            select w.ownerUniqueId as uniqueId from WorkspaceEntity w
            where w.uniqueId = :workspaceUniqueId
            """)
    Optional<UniqueIdRow> findOwnerByUniqueId(@Param("workspaceUniqueId") Long workspaceUniqueId);
}
