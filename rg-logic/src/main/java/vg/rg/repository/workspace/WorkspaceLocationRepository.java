package vg.rg.repository.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.workspace.WorkspaceLocationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Every read here takes a workspace, so a query cannot be written unscoped. That is a property of the
 * signatures rather than a convention to remember — the reason the mandatory {@code workspaceUniqueId}
 * column was preferred over a nullable scope discriminator.
 */
public interface WorkspaceLocationRepository extends UniqueIdJpaRepository<WorkspaceLocationEntity> {

    Page<WorkspaceLocationEntity> findByWorkspaceUniqueId(UniqueId workspaceUniqueId, Pageable pageable);

    Page<WorkspaceLocationEntity> findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
            UniqueId workspaceUniqueId, String name, Pageable pageable);

    boolean existsByUniqueIdAndWorkspaceUniqueId(Long uniqueId, UniqueId workspaceUniqueId);

    /**
     * Bounding-box prefilter over the indexed workspace/latitude columns, scoped to one workspace.
     * Callers refine the candidates with an exact great-circle distance, which keeps the query portable.
     */
    @Query("""
            select l from WorkspaceLocationEntity l
            where l.workspaceUniqueId = :workspaceUniqueId
              and l.latitude between :minLat and :maxLat
              and l.longitude between :minLng and :maxLng
            """)
    List<WorkspaceLocationEntity> findWithinBoundingBox(
            @Param("workspaceUniqueId") UniqueId workspaceUniqueId,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng);

    long countByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    void deleteByWorkspaceUniqueId(UniqueId workspaceUniqueId);

    /**
     * The workspace-scope lookup for a location addressed by its own identifier: one query that joins
     * through to the owning workspace, so the authority check costs a single round trip no matter how
     * many levels separate a type from its workspace.
     *
     * <p>An ad-hoc entity join, because the entity carries a plain {@code workspaceUniqueId} column
     * rather than a {@code @ManyToOne}. Aliases feed a {@link WorkspaceScopeRow} interface projection;
     * Hibernate applies the {@code UniqueId} converter to each selected attribute, so the projection is
     * already typed.
     */
    @Query("""
            select l.workspaceUniqueId as workspaceUniqueId, w.ownerUniqueId as ownerUniqueId
            from WorkspaceLocationEntity l
              join WorkspaceEntity w on w.uniqueId = l.workspaceUniqueId
            where l.uniqueId = :uniqueId
            """)
    Optional<WorkspaceScopeRow> findWorkspaceScopeByUniqueId(@Param("uniqueId") Long uniqueId);
}
