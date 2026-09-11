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
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
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
    private final WorkspaceLocationRepository locationRepository;
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
        var entity = WorkspaceEventEntity.builder()
                .workspaceUniqueId(workspaceId)
                .title(title)
                .startAt(model.getStartAt())
                .endAt(model.getEndAt())
                .locationUniqueId(model.getLocationUniqueId())
                .eventType(model.getEventType())
                .maxParticipantCount(model.getMaxParticipantCount())
                .isPublished(model.isPublished())
                .build();
        return mapper.toModel(repository.saveWithNewUniqueId(entity, uniqueIdService));
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
        return events.map(mapper::toModel);
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
        entity.setPublished(model.isPublished());
        return mapper.toModel(repository.save(entity));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#eventId, '" + LocalPermissions.WorkspaceEvent.DELETE + "')")
    public void delete(UniqueId eventId) {
        Objects.requireNonNull(eventId, "eventId");
        repository.delete(repository.findById(eventId).orElseThrow(EntityNotFoundException::new));
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
}
