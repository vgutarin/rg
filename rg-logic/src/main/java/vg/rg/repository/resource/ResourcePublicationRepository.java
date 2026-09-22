package vg.rg.repository.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.resource.ResourcePublicationEntity;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** Persistence for generic publication state keyed by the published resource. */
public interface ResourcePublicationRepository extends JpaRepository<ResourcePublicationEntity, Long> {

    /** Advances the publication version even when only its separately stored rules change. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ResourcePublicationEntity p
            set p.visibilityMode = :mode, p.version = p.version + 1,
                p.lastEditor = :editor, p.updatedAt = :updatedAt
            where p.resourceUniqueId = :resourceId and p.version = :version
            """)
    int replacePolicy(@Param("resourceId") Long resourceId, @Param("version") int version,
                      @Param("mode") ResourceVisibilityMode mode, @Param("editor") UniqueId editor,
                      @Param("updatedAt") Instant updatedAt);

    String WHERE_AUDIENCE_VISIBILITY = """
            where
                p.visibleSince is not null and (
                    p.visibilityMode = vg.rg.model.resource.ResourceVisibilityMode.PUBLIC
                    or (
                        :viewer is not null
                        and p.visibilityMode = vg.rg.model.resource.ResourceVisibilityMode.RESTRICTED
                        and exists (
                            select 1 from ResourceVisibilityRuleEntity allowRule
                            where allowRule.resourceUniqueId = p.resourceUniqueId
                              and allowRule.effect = vg.rg.model.resource.ResourceVisibilityEffect.ALLOW
                              and (
                                  (allowRule.subjectKind = vg.rg.model.resource.ResourceVisibilitySubjectKind.USER
                                   and allowRule.subjectUniqueId = :viewer)
                                  or (allowRule.subjectKind = vg.rg.model.resource.ResourceVisibilitySubjectKind.WORKSPACE
                                      and exists (
                                          select 1 from WorkspaceParticipantEntity participant
                                          where participant.workspaceUniqueId = allowRule.subjectUniqueId
                                            and participant.userUniqueId = :viewer
                                      ))
                              )
                        )
                        and not exists (
                            select 1 from ResourceVisibilityRuleEntity denyRule
                            where denyRule.resourceUniqueId = p.resourceUniqueId
                              and denyRule.effect = vg.rg.model.resource.ResourceVisibilityEffect.DENY
                              and (
                                  (denyRule.subjectKind = vg.rg.model.resource.ResourceVisibilitySubjectKind.USER
                                   and denyRule.subjectUniqueId = :viewer)
                                  or (denyRule.subjectKind = vg.rg.model.resource.ResourceVisibilitySubjectKind.WORKSPACE
                                      and exists (
                                          select 1 from WorkspaceParticipantEntity participant
                                          where participant.workspaceUniqueId = denyRule.subjectUniqueId
                                            and participant.userUniqueId = :viewer
                                      ))
                              )
                        )
                    )
                )
            """;

    /**
     * Audience-visible workspace events with all display data selected before paging.
     *
     * <p>Viewer-scoped joins are cardinality-safe because workspace participants and pending membership
     * requests are each unique per workspace and user.
     */
    @Query(
            value = """
                    select e.uniqueId as uniqueId,
                           e.title as title,
                           e.startAt as startAt,
                           e.endAt as endAt,
                           e.eventType as eventType,
                           e.maxParticipantCount as maxParticipantCount,
                           l.latitude as locationLatitude,
                           l.longitude as locationLongitude,
                           l.name as locationName,
                           l.description as locationDescription,
                           l.googlePlaceId as locationGooglePlaceId,
                           case when participant.uniqueId is null then false else true end as membershipBound,
                           case when request.uniqueId is null then false else true end as membershipPending
                    from ResourcePublicationEntity p
                      join WorkspaceEventEntity e on e.uniqueId = p.resourceUniqueId
                      join WorkspaceLocationEntity l on l.uniqueId = cast(e.locationUniqueId as long)
                      left join WorkspaceParticipantEntity participant
                        on participant.workspaceUniqueId = e.workspaceUniqueId and participant.userUniqueId = :viewer
                      left join WorkspaceMembershipRequestEntity request
                        on request.workspaceUniqueId = e.workspaceUniqueId and request.pendingUserUniqueId = :viewer
                    """ + WHERE_AUDIENCE_VISIBILITY + " order by e.startAt asc, e.title asc, e.uniqueId asc",
            countQuery = """
                    select count(e)
                    from ResourcePublicationEntity p
                      join WorkspaceEventEntity e on e.uniqueId = p.resourceUniqueId
                    """ + WHERE_AUDIENCE_VISIBILITY
    )
    Page<WorkspaceEventAudienceRow> findVisibleWorkspaceEventsForAudience(
            @Param("viewer") UniqueId viewer, Pageable pageable);

    /**
     * Checks generic publication visibility for a known resource and authenticated user.
     * Workspace rules match when that user is a bound participant of the rule's workspace.
     */
    @Query("select case when count(p) > 0 then true else false end from ResourcePublicationEntity p "
            + WHERE_AUDIENCE_VISIBILITY + " and p.resourceUniqueId = :#{#resourceUniqueId.longValue}")
    boolean isResourceVisibleForUser(@Param("resourceUniqueId") UniqueId resourceUniqueId,
                                     @Param("viewer") UniqueId userUniqueId);

}
