package vg.rg.repository.workspace;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.workspace.WorkspaceEventRegistrationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Workspace-scoped event-registration persistence.
 *
 * <p>Every read is scoped by the event, or by the workspace via a join through the event — a registration
 * does not store its workspace, so anything workspace-wide resolves through {@code event_unique_id}. The
 * delete finders exist for referential cleanup: the foreign keys are deliberately restricting, so
 * registrations must be removed before the event or participant they point at.
 */
public interface WorkspaceEventRegistrationRepository
        extends UniqueIdJpaRepository<WorkspaceEventRegistrationEntity> {

    /**
     * One event's roster in display order.
     *
     * <p>Spelled as an explicit {@code @Query} rather than a derived name because the ordering column is
     * itself called {@code orderBy}: a derived {@code ...OrderByOrderBy...} makes Spring Data see the
     * {@code OrderBy} keyword twice and refuse the method.
     */
    @Query("""
            select r from WorkspaceEventRegistrationEntity r
            where r.eventUniqueId = :eventUniqueId
            order by r.orderBy asc, r.uniqueId asc
            """)
    List<WorkspaceEventRegistrationEntity> findRosterByEventUniqueId(
            @Param("eventUniqueId") UniqueId eventUniqueId);

    /** The last registration in an event's roster, whose {@code orderBy} a new one is appended after. */
    @Query("""
            select r from WorkspaceEventRegistrationEntity r
            where r.eventUniqueId = :eventUniqueId
            order by r.orderBy desc, r.uniqueId desc
            limit 1
            """)
    Optional<WorkspaceEventRegistrationEntity> findLastByEventUniqueId(
            @Param("eventUniqueId") UniqueId eventUniqueId);

    boolean existsByEventUniqueIdAndParticipantUniqueId(
            UniqueId eventUniqueId, UniqueId participantUniqueId);

    long countByEventUniqueId(UniqueId eventUniqueId);

    /**
     * Registered counts for a set of events at once, so a page of events costs one query rather than one
     * per row. Only events with at least one registration appear; a caller reads an absent event as zero.
     */
    @Query("""
            select r.eventUniqueId as uniqueId, count(r) as total
            from WorkspaceEventRegistrationEntity r
            where r.eventUniqueId in :eventUniqueIds
            group by r.eventUniqueId
            """)
    List<EventRegistrationCountRow> countByEventUniqueIds(
            @Param("eventUniqueIds") Collection<UniqueId> eventUniqueIds);

    /** How many registrations a workspace holds, counted across its events for cascade diagnostics. */
    @Query("""
            select count(r) from WorkspaceEventRegistrationEntity r
              join WorkspaceEventEntity e on e.uniqueId = r.eventUniqueId
            where e.workspaceUniqueId = :workspaceUniqueId
            """)
    long countByWorkspaceUniqueId(@Param("workspaceUniqueId") UniqueId workspaceUniqueId);

    /**
     * Removes every registration belonging to a workspace, resolved through each registration's event.
     * A bulk delete because the workspace is not a column here; scoped to the one workspace by the
     * sub-select, so it cannot reach another's rows.
     */
    @Modifying
    @Query("""
            delete from WorkspaceEventRegistrationEntity r
            where r.eventUniqueId in (
                select e.uniqueId from WorkspaceEventEntity e where e.workspaceUniqueId = :workspaceUniqueId)
            """)
    void deleteByWorkspaceUniqueId(@Param("workspaceUniqueId") UniqueId workspaceUniqueId);

    void deleteByEventUniqueId(UniqueId eventUniqueId);

    void deleteByParticipantUniqueId(UniqueId participantUniqueId);

    /**
     * The workspace-scope lookup for a registration addressed by its own identifier: one query joining
     * through the event to the owning workspace, so an authority check costs a single round trip. See
     * {@link WorkspaceEventRepository#findWorkspaceScopeByUniqueId(Long)} for why this is an ad-hoc join
     * feeding an interface projection.
     */
    @Query("""
            select e.workspaceUniqueId as workspaceUniqueId, w.ownerUniqueId as ownerUniqueId
            from WorkspaceEventRegistrationEntity r
              join WorkspaceEventEntity e on e.uniqueId = r.eventUniqueId
              join WorkspaceEntity w on w.uniqueId = e.workspaceUniqueId
            where r.uniqueId = :uniqueId
            """)
    Optional<WorkspaceScopeRow> findWorkspaceScopeByUniqueId(@Param("uniqueId") Long uniqueId);
}
