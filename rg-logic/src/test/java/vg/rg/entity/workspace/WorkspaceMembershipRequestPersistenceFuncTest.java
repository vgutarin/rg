package vg.rg.entity.workspace;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.BaseFuncTest;
import vg.rg.entity.resource.ResourcePublicationEntity;
import vg.rg.entity.resource.ResourceVisibilityRuleEntity;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceMembershipApprovalTarget;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.model.resource.ResourceVisibilityEffect;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;
import vg.rg.repository.resource.ResourcePublicationRepository;
import vg.rg.repository.resource.ResourceVisibilityRuleRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceMembershipRequestRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.rg.service.resource.ResourcePublicationService;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

/** Exercises the request table's encryption converter and one-request-per-workspace-user invariant. */
class WorkspaceMembershipRequestPersistenceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId REQUESTER = nextUniqueId();

    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMembershipRequestRepository requestRepository;
    @Autowired WorkspaceParticipantRepository participantRepository;
    @Autowired WorkspaceEventRepository eventRepository;
    @Autowired ResourcePublicationRepository publicationRepository;
    @Autowired ResourceVisibilityRuleRepository visibilityRuleRepository;
    @Autowired WorkspaceLocationRepository locationRepository;
    @Autowired UniqueIdService uniqueIdService;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired WorkspaceMembershipRequestService membershipRequestService;
    @Autowired ResourcePublicationService publicationService;

    private UniqueId workspaceId;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        var workspace = workspaceRepository.saveWithNewUniqueId(
                WorkspaceEntity.builder().ownerUniqueId(OWNER).build(), uniqueIdService);
        workspaceId = new UniqueId(workspace.getUniqueId());
    }

    @AfterEach
    void cleanUp() {
        visibilityRuleRepository.deleteAll();
        publicationRepository.deleteAll();
        requestRepository.deleteAll();
        eventRepository.deleteAll();
        locationRepository.deleteAll();
        participantRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    @Transactional
    void descriptor_isEncryptedAndRoundTripsThroughHibernate() {
        var saved = savePending("Private account name");
        entityManager.flush();
        var raw = jdbcTemplate.queryForObject(
                "select descriptor from rg_workspace_membership_request where unique_id = ?",
                byte[].class, saved.getUniqueId());
        entityManager.clear();

        var reloaded = entityManager.find(WorkspaceMembershipRequestEntity.class, saved.getUniqueId());

        assertThat(raw).isNotNull();
        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("Private account name");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getDescriptor()).isEqualTo(ParticipantDescriptor.of("Private account name", null));
    }

    @Test
    void databasePermitsOnlyOneRequestForAWorkspaceUser() {
        savePending("First");
        requestRepository.flush();

        assertThatThrownBy(() -> {
            savePending("Second");
            requestRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removedRequest_releasesTheSlotForANewRequest() {
        var removed = savePending("First");
        requestRepository.delete(removed);
        requestRepository.flush();

        savePending("Second");
        requestRepository.flush();

        assertThat(requestRepository.countByWorkspaceUniqueId(workspaceId)).isOne();
    }

    @Test
    @Transactional
    void persistedEnums_areStoredByOrdinal() {
        var event = saveEvent(ResourceVisibilityMode.RESTRICTED);
        var rule = visibilityRuleRepository.saveWithNewUniqueId(ResourceVisibilityRuleEntity.builder()
                .resourceUniqueId(new UniqueId(event.getUniqueId()))
                .effect(ResourceVisibilityEffect.DENY)
                .subjectKind(ResourceVisibilitySubjectKind.WORKSPACE_GROUP)
                .subjectUniqueId(new UniqueId(92101L)).build(), uniqueIdService);
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
                "select visibility_mode from rg_resource_publication where resource_unique_id = ?", Integer.class,
                event.getUniqueId())).isEqualTo(ResourceVisibilityMode.RESTRICTED.ordinal());
        assertThat(jdbcTemplate.queryForMap("""
                select author, last_editor, created_at, updated_at
                from rg_resource_publication where resource_unique_id = ?
                """, event.getUniqueId()))
                .containsEntry("AUTHOR", OWNER.getLongValue())
                .containsKeys("LAST_EDITOR", "CREATED_AT", "UPDATED_AT");
        assertThat(jdbcTemplate.queryForObject(
                "select effect from rg_resource_visibility_rule where unique_id = ?", Integer.class,
                rule.getUniqueId())).isEqualTo(ResourceVisibilityEffect.DENY.ordinal());
        assertThat(jdbcTemplate.queryForObject(
                "select subject_kind from rg_resource_visibility_rule where unique_id = ?", Integer.class,
                rule.getUniqueId())).isEqualTo(ResourceVisibilitySubjectKind.WORKSPACE_GROUP.ordinal());
        assertTinyintNotNull("rg_resource_publication", "visibility_mode", "0");
        assertTinyintNotNull("rg_resource_visibility_rule", "effect", null);
        assertTinyintNotNull("rg_resource_visibility_rule", "subject_kind", null);
    }

    @Test
    void approval_trimsTheAccountNameBeforeCreatingTheBoundParticipant() {
        var request = savePending("  Requester  ");

        membershipRequestService.approve(workspaceId, new UniqueId(request.getUniqueId()),
                WorkspaceMembershipApprovalTarget.CREATE_PARTICIPANT, null, "ignored");

        assertThat(participantRepository.findByWorkspaceUniqueId(workspaceId))
                .extracting(participant -> participant.getDescriptor().label())
                .containsExactly("Requester");
        assertThat(requestRepository.findById(new UniqueId(request.getUniqueId()))).isEmpty();
    }

    @Test
    void rejection_usesTheWorkspaceScopeAndDeletesTheRequest() {
        var request = savePending("Requester");

        membershipRequestService.reject(workspaceId, new UniqueId(request.getUniqueId()));

        assertThat(requestRepository.findById(new UniqueId(request.getUniqueId()))).isEmpty();
    }

    @Test
    @Transactional
    void workspaceLock_isResolvedFromTheOriginEvent() {
        var event = saveEvent(ResourceVisibilityMode.PUBLIC);
        entityManager.flush();

        var locked = workspaceRepository.lockByEventUniqueId(event.getUniqueId());

        assertThat(locked).isPresent().get().extracting(WorkspaceEntity::getUniqueId)
                .isEqualTo(workspaceId.getLongValue());
    }

    @Test
    void audienceQuery_filtersBeforePagingForPublicRestrictedAndDeniedEvents() {
        var publicEvent = saveEvent(ResourceVisibilityMode.PUBLIC);
        var draft = saveEvent(ResourceVisibilityMode.PUBLIC);
        var draftPublication = publicationRepository.findById(draft.getUniqueId()).orElseThrow();
        draftPublication.setVisibleSince(null);
        publicationRepository.save(draftPublication);
        var allowed = saveEvent(ResourceVisibilityMode.RESTRICTED);
        visibilityRuleRepository.saveWithNewUniqueId(ResourceVisibilityRuleEntity.builder()
                .resourceUniqueId(new UniqueId(allowed.getUniqueId()))
                .effect(ResourceVisibilityEffect.ALLOW)
                .subjectKind(ResourceVisibilitySubjectKind.USER)
                .subjectUniqueId(REQUESTER).build(), uniqueIdService);
        var denied = saveEvent(ResourceVisibilityMode.RESTRICTED);
        visibilityRuleRepository.saveWithNewUniqueId(ResourceVisibilityRuleEntity.builder()
                .resourceUniqueId(new UniqueId(denied.getUniqueId()))
                .effect(ResourceVisibilityEffect.ALLOW)
                .subjectKind(ResourceVisibilitySubjectKind.USER)
                .subjectUniqueId(REQUESTER).build(), uniqueIdService);
        var deniedAudienceWorkspace = workspaceRepository.saveWithNewUniqueId(
                WorkspaceEntity.builder().ownerUniqueId(OWNER).build(), uniqueIdService);
        var deniedAudienceWorkspaceId = new UniqueId(deniedAudienceWorkspace.getUniqueId());
        participantRepository.saveWithNewUniqueId(WorkspaceParticipantEntity.builder()
                .workspaceUniqueId(deniedAudienceWorkspaceId)
                .userUniqueId(REQUESTER)
                .descriptor(ParticipantDescriptor.of("Denied member", null)).build(), uniqueIdService);
        visibilityRuleRepository.saveWithNewUniqueId(ResourceVisibilityRuleEntity.builder()
                .resourceUniqueId(new UniqueId(denied.getUniqueId()))
                .effect(ResourceVisibilityEffect.DENY)
                .subjectKind(ResourceVisibilitySubjectKind.WORKSPACE)
                .subjectUniqueId(deniedAudienceWorkspaceId).build(), uniqueIdService);

        assertThat(publicationRepository.isResourceVisibleForUser(new UniqueId(publicEvent.getUniqueId()), REQUESTER)).isTrue();
        assertThat(publicationRepository.isResourceVisibleForUser(new UniqueId(draft.getUniqueId()), REQUESTER)).isFalse();
        assertThat(publicationRepository.isResourceVisibleForUser(new UniqueId(allowed.getUniqueId()), REQUESTER)).isTrue();
        assertThat(publicationRepository.isResourceVisibleForUser(new UniqueId(denied.getUniqueId()), REQUESTER)).isFalse();
        authenticate(REQUESTER);

        var page = publicationService.browseWorkspaceEvents(PageRequest.of(0, 10));

        assertThat(page).extracting(model -> model.getUniqueId().getLongValue())
                .containsExactly(publicEvent.getUniqueId(), allowed.getUniqueId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void audienceQuery_returnsTheJoinedSafeLocationAndMembershipState() {
        var event = saveEvent(ResourceVisibilityMode.PUBLIC);
        participantRepository.saveWithNewUniqueId(WorkspaceParticipantEntity.builder()
                .workspaceUniqueId(workspaceId).userUniqueId(REQUESTER)
                .descriptor(ParticipantDescriptor.of("Requester", null)).build(), uniqueIdService);
        savePending("Requester");

        var page = publicationRepository.findVisibleWorkspaceEventsForAudience(REQUESTER, PageRequest.of(0, 10));

        assertThat(page).singleElement().satisfies(row -> {
            assertThat(row.getUniqueId()).isEqualTo(event.getUniqueId());
            assertThat(row.getLocationName()).isEqualTo("Courts");
            assertThat(row.getLocationDescription()).isEqualTo("Covered courts");
            assertThat(row.getLocationLatitude()).isEqualByComparingTo("50.450100");
            assertThat(row.getLocationLongitude()).isEqualByComparingTo("30.523400");
            assertThat(row.getLocationGooglePlaceId()).isEqualTo("place-courts");
            assertThat(row.isMembershipBound()).isTrue();
            assertThat(row.isMembershipPending()).isTrue();
        });
    }

    @Test
    void requestJoin_rechecksAudienceAfterConsentAndPersistsOnlyOneRequest() {
        var event = saveEvent(ResourceVisibilityMode.PUBLIC);
        var eventId = new UniqueId(event.getUniqueId());
        authenticate(REQUESTER);
        membershipRequestService.requestJoin(eventId);
        membershipRequestService.requestJoin(eventId);

        var pending = requestRepository.findByWorkspaceUniqueIdAndPendingUserUniqueId(workspaceId, REQUESTER);
        assertThat(pending).hasValueSatisfying(request -> {
            assertThat(request.getOriginEventUniqueId()).isEqualTo(eventId);
            assertThat(request.getPendingUserUniqueId()).isEqualTo(REQUESTER);
        });
        assertThat(requestRepository.countByWorkspaceUniqueId(workspaceId)).isOne();
    }

    @Test
    void requestJoin_refusesAnEventUnpublishedDuringTheConsentJourney() {
        var event = saveEvent(ResourceVisibilityMode.PUBLIC);
        var eventId = new UniqueId(event.getUniqueId());
        authenticate(REQUESTER);
        assertThat(publicationService.isResourceVisibleForCurrentUser(eventId)).isTrue();
        var publication = publicationRepository.findById(event.getUniqueId()).orElseThrow();
        publication.setVisibleSince(null);
        publicationRepository.saveAndFlush(publication);

        assertThatThrownBy(() -> membershipRequestService.requestJoin(eventId))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        assertThat(requestRepository.countByWorkspaceUniqueId(workspaceId)).isZero();
    }

    @Test
    void requestJoin_doesNotCreateARequestForAnAudienceIneligibleEvent() {
        var event = saveEvent(ResourceVisibilityMode.RESTRICTED);
        authenticate(REQUESTER);

        assertThatThrownBy(() -> membershipRequestService.requestJoin(new UniqueId(event.getUniqueId())))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        assertThat(requestRepository.countByWorkspaceUniqueId(workspaceId)).isZero();
    }

    private WorkspaceMembershipRequestEntity savePending(String name) {
        return requestRepository.saveWithNewUniqueId(WorkspaceMembershipRequestEntity.builder()
                .workspaceUniqueId(workspaceId).pendingUserUniqueId(REQUESTER)
                .descriptor(ParticipantDescriptor.of(name, null))
                .build(), uniqueIdService);
    }

    private WorkspaceEventEntity saveEvent(ResourceVisibilityMode visibilityMode) {
        var location = locationRepository.saveWithNewUniqueId(WorkspaceLocationEntity.builder()
                .workspaceUniqueId(workspaceId).name("Courts").description("Covered courts")
                .latitude(new BigDecimal("50.450100")).longitude(new BigDecimal("30.523400"))
                .googlePlaceId("place-courts").build(), uniqueIdService);
        var event = eventRepository.saveWithNewUniqueId(WorkspaceEventEntity.builder()
                .workspaceUniqueId(workspaceId).title("Training")
                .startAt(java.time.Instant.parse("2026-09-13T09:00:00Z"))
                .locationUniqueId(new UniqueId(location.getUniqueId())).eventType(WorkspaceEventType.PADEL)
                .maxParticipantCount(8).build(), uniqueIdService);
        publicationRepository.save(ResourcePublicationEntity.builder().resourceUniqueId(event.getUniqueId())
                .visibleSince(java.time.Instant.parse("2026-09-13T09:00:00Z")).visibilityMode(visibilityMode).build());
        return event;
    }

    private void assertTinyintNotNull(String tableName, String columnName, String defaultValue) {
        var column = jdbcTemplate.queryForMap("""
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, tableName, columnName);

        assertThat(column).containsEntry("DATA_TYPE", "tinyint").containsEntry("IS_NULLABLE", "NO");
        assertThat(column.get("COLUMN_DEFAULT")).isEqualTo(defaultValue);
    }
}
