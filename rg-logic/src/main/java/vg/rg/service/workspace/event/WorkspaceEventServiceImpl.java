package vg.rg.service.workspace.event;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.entity.workspace.WorkspaceEventEntity;
import vg.rg.mapper.workspace.WorkspaceEventMapper;
import vg.rg.model.resource.ResourcePublication;
import vg.rg.model.resource.ResourceVisibilityPolicy;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceMembershipRequestRepository;
import vg.rg.service.resource.ResourcePublicationService;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class WorkspaceEventServiceImpl implements WorkspaceEventService {

    static final String TITLE_REQUIRED = "workspace.event.error.title-required";
    static final String TITLE_TOO_LONG = "workspace.event.error.title-too-long";
    static final String START_REQUIRED = "workspace.event.error.start-required";
    static final String TYPE_REQUIRED = "workspace.event.error.type-required";
    static final String MAX_PARTICIPANT_COUNT_REQUIRED = "workspace.event.error.max-participant-count-required";
    static final String MAX_PARTICIPANT_COUNT_NOT_POSITIVE = "workspace.event.error.max-participant-count-not-positive";
    static final String END_BEFORE_START = "workspace.event.error.end-before-start";
    static final String LOCATION_REQUIRED = "workspace.event.error.location-required";
    static final String LOCATION_OUTSIDE_WORKSPACE = "workspace.event.error.location-outside-workspace";
    private static final int TITLE_MAX_LENGTH = 512;

    private final UniqueIdService uniqueIdService;
    private final WorkspaceEventRepository repository;
    private final WorkspaceEventRegistrationRepository registrationRepository;
    private final ResourcePublicationService publicationService;
    private final WorkspaceLocationRepository locationRepository;
    private final WorkspaceMembershipRequestRepository membershipRequestRepository;
    private final WorkspaceEventMapper mapper;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '" + LocalPermissions.WorkspaceEvent.CREATE + "')")
    public WorkspaceEventModel create(UniqueId workspaceId, WorkspaceEventModel model) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(model, "model");
        var title = titleOf(model);
        validateSchedule(model);
        validateLocation(workspaceId, model.getLocationUniqueId());
        var policy = policyFrom(model);
        validatePolicy(policy);
        var entity = WorkspaceEventEntity.builder()
                .workspaceUniqueId(workspaceId)
                .title(title)
                .startAt(model.getStartAt())
                .endAt(model.getEndAt())
                .locationUniqueId(model.getLocationUniqueId())
                .eventType(model.getEventType())
                .maxParticipantCount(model.getMaxParticipantCount()).build();
        var saved = repository.saveWithNewUniqueId(entity, uniqueIdService);
        var publication = publicationService.create(new UniqueId(saved.getUniqueId()), model.isPublished(),
                policy.mode(), policy.rules());
        return toModel(saved, publication);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '" + LocalPermissions.WorkspaceEvent.LIST + "')")
    public Page<WorkspaceEventModel> browse(UniqueId workspaceId, String titleFilter, Pageable pageable) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(pageable, "pageable");
        var filter = titleFilter == null ? "" : titleFilter.trim();
        var events = filter.isBlank()
                ? repository.findByWorkspaceUniqueId(workspaceId, pageable)
                : repository.findByWorkspaceUniqueIdAndTitleContainingIgnoreCase(workspaceId, filter, pageable);
        return events.map(this::toModel);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#model.uniqueId, '" + LocalPermissions.WorkspaceEvent.UPDATE + "')")
    public WorkspaceEventModel update(WorkspaceEventModel model) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(model.getUniqueId(), "uniqueId");
        var entity = repository.findById(model.getUniqueId()).orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != model.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(WorkspaceEventEntity.class, model.getUniqueId());
        }
        var title = titleOf(model);
        validateSchedule(model);
        validateLocation(entity.getWorkspaceUniqueId(), model.getLocationUniqueId());
        entity.setTitle(title);
        entity.setStartAt(model.getStartAt());
        entity.setEndAt(model.getEndAt());
        entity.setLocationUniqueId(model.getLocationUniqueId());
        entity.setEventType(model.getEventType());
        entity.setMaxParticipantCount(model.getMaxParticipantCount());
        var saved = repository.save(entity);
        var publication = publicationService.updatePublicationState(model.getUniqueId(), model.getPublicationVersion(),
                model.isPublished());
        return toModel(saved, publication);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#eventId, '" + LocalPermissions.WorkspaceEvent.UPDATE + "')")
    public WorkspaceEventModel replaceVisibilityPolicy(
            UniqueId eventId, int version, ResourceVisibilityPolicy policy) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(policy, "policy");
        validatePolicy(policy);
        var event = repository.findById(eventId).orElseThrow(EntityNotFoundException::new);
        var publication = publicationService.replaceVisibilityPolicy(eventId, version,
                policy.mode(), policy.rules());
        return toModel(event, publication);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#eventId, '" + LocalPermissions.WorkspaceEvent.DELETE + "')")
    public void delete(UniqueId eventId) {
        Objects.requireNonNull(eventId, "eventId");
        var entity = repository.findById(eventId).orElseThrow(EntityNotFoundException::new);
        // The FK from a registration to its event is deliberately restricting, so the roster must go
        // first or the delete fails with a constraint violation.
        registrationRepository.deleteByEventUniqueId(eventId);
        publicationService.delete(eventId);
        membershipRequestRepository.clearOriginEventUniqueId(eventId);
        repository.delete(entity);
    }

    private static String titleOf(WorkspaceEventModel model) {
        var title = model.getTitle() == null ? null : model.getTitle().trim();
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException(TITLE_REQUIRED);
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(TITLE_TOO_LONG);
        }
        return title;
    }

    private static void validateSchedule(WorkspaceEventModel model) {
        if (model.getStartAt() == null) {
            throw new IllegalArgumentException(START_REQUIRED);
        }
        if (model.getEventType() == null) {
            throw new IllegalArgumentException(TYPE_REQUIRED);
        }
        if (model.getMaxParticipantCount() == null) {
            throw new IllegalArgumentException(MAX_PARTICIPANT_COUNT_REQUIRED);
        }
        if (model.getMaxParticipantCount() <= 0) {
            throw new IllegalArgumentException(MAX_PARTICIPANT_COUNT_NOT_POSITIVE);
        }
        if (model.getEndAt() != null && model.getEndAt().isBefore(model.getStartAt())) {
            throw new IllegalArgumentException(END_BEFORE_START);
        }
    }

    private void validateLocation(UniqueId workspaceId, UniqueId locationId) {
        if (locationId == null) {
            throw new IllegalArgumentException(LOCATION_REQUIRED);
        }
        if (!locationRepository.existsByUniqueIdAndWorkspaceUniqueId(locationId.getLongValue(), workspaceId)) {
            throw new IllegalArgumentException(LOCATION_OUTSIDE_WORKSPACE);
        }
    }

    private WorkspaceEventModel toModel(WorkspaceEventEntity entity) {
        var publication = publicationService.ensureDraftPublication(new UniqueId(entity.getUniqueId()));
        return toModel(entity, publication);
    }

    private WorkspaceEventModel toModel(WorkspaceEventEntity entity, ResourcePublication publication) {
        var model = mapper.toModel(entity);
        model.setPublished(publication.isPublished());
        model.setPublicationVersion(publication.version());
        model.setVisibilityPolicy(new ResourceVisibilityPolicy(publication.visibilityMode(), publication.rules()));
        return model;
    }

    private static ResourceVisibilityPolicy policyFrom(WorkspaceEventModel model) {
        return model.getVisibilityPolicy() == null
                ? ResourceVisibilityPolicy.publicVisibility()
                : model.getVisibilityPolicy();
    }

    private static void validatePolicy(ResourceVisibilityPolicy policy) {
        if (policy.rules().stream().anyMatch(rule -> rule.subjectKind()
                == ResourceVisibilitySubjectKind.WORKSPACE_GROUP)) {
            throw new IllegalArgumentException("resource.visibility.error.group-not-supported");
        }
    }
}
