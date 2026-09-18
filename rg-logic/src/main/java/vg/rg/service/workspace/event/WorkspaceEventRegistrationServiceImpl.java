package vg.rg.service.workspace.event;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.entity.workspace.WorkspaceEventRegistrationEntity;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.mapper.workspace.WorkspaceEventRegistrationMapper;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.rg.repository.workspace.EventRegistrationCountRow;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class WorkspaceEventRegistrationServiceImpl implements WorkspaceEventRegistrationService {

    // Stable outcome codes; the UI owns their translation, and neither carries the value that was
    // rejected.
    static final String ALREADY_REGISTERED = "workspace.event.registration.error.already-registered";
    static final String PARTICIPANT_NOT_IN_WORKSPACE =
            "workspace.event.registration.error.participant-not-in-workspace";

    private final UniqueIdService uniqueIdService;
    private final WorkspaceEventRegistrationRepository repository;
    private final WorkspaceEventRepository eventRepository;
    private final WorkspaceParticipantRepository participantRepository;
    private final WorkspaceEventRegistrationMapper mapper;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#eventId, '"
            + LocalPermissions.WorkspaceEvent.MANAGE_PARTICIPANTS + "')")
    public WorkspaceEventRegistrationModel register(UniqueId eventId, UniqueId participantId) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(participantId, "participantId");
        var event = eventRepository.findById(eventId).orElseThrow(EntityNotFoundException::new);
        var participant = participantRepository.findById(participantId)
                .orElseThrow(EntityNotFoundException::new);
        // The participant must be content of the very workspace the event lives in. Compared here rather
        // than trusted from the caller: the scope is a property of the two resources, not of the request.
        if (!event.getWorkspaceUniqueId().equals(participant.getWorkspaceUniqueId())) {
            throw new IllegalArgumentException(PARTICIPANT_NOT_IN_WORKSPACE);
        }
        if (repository.existsByEventUniqueIdAndParticipantUniqueId(eventId, participantId)) {
            throw new IllegalArgumentException(ALREADY_REGISTERED);
        }

        var entity = WorkspaceEventRegistrationEntity.builder()
                .eventUniqueId(eventId)
                .participantUniqueId(participantId)
                .orderBy(nextOrderBy(eventId))
                .build();
        var saved = repository.saveWithNewUniqueId(entity, uniqueIdService);
        return mapper.toModel(saved, labelOf(participant));
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#eventId, '"
            + LocalPermissions.WorkspaceEvent.MANAGE_PARTICIPANTS + "')")
    public List<WorkspaceEventRegistrationModel> list(UniqueId eventId) {
        Objects.requireNonNull(eventId, "eventId");
        var registrations = repository.findRosterByEventUniqueId(eventId);
        if (registrations.isEmpty()) {
            return List.of();
        }
        // One roster read for the whole event, opened in memory to resolve labels -- bounded by the
        // workspace's participant limit, the same reason the participant service reads a roster at once.
        // The workspace comes from the event, since a registration no longer stores it.
        var event = eventRepository.findById(eventId).orElseThrow(EntityNotFoundException::new);
        var labels = labelsByParticipant(event.getWorkspaceUniqueId());
        return registrations.stream()
                .map(entity -> mapper.toModel(entity, labels.get(entity.getParticipantUniqueId())))
                .toList();
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#registrationId, '"
            + LocalPermissions.WorkspaceEventRegistration.DELETE + "')")
    public void unregister(UniqueId registrationId) {
        Objects.requireNonNull(registrationId, "registrationId");
        var entity = repository.findById(registrationId).orElseThrow(EntityNotFoundException::new);
        // Gaps in order_by left behind are harmless: the roster is always read ordered by it.
        repository.delete(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#registrationId, '"
            + LocalPermissions.WorkspaceEventRegistration.REORDER + "')")
    public void moveUp(UniqueId registrationId) {
        move(registrationId, -1);
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#registrationId, '"
            + LocalPermissions.WorkspaceEventRegistration.REORDER + "')")
    public void moveDown(UniqueId registrationId) {
        move(registrationId, 1);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.WorkspaceEvent.LIST + "')")
    public Map<UniqueId, Long> countByEvents(UniqueId workspaceId, Collection<UniqueId> eventIds) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(eventIds, "eventIds");
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return repository.countByEventUniqueIds(eventIds).stream()
                .collect(Collectors.toMap(EventRegistrationCountRow::getUniqueId,
                        EventRegistrationCountRow::getTotal));
    }

    /**
     * Swaps a registration's {@code orderBy} with its neighbour in the requested direction. A no-op at
     * the corresponding end. The swap keeps values distinct because registrations are appended at
     * {@code max + 1}, so the ordered list never has two equal positions to swap.
     */
    private void move(UniqueId registrationId, int direction) {
        Objects.requireNonNull(registrationId, "registrationId");
        var moving = repository.findById(registrationId).orElseThrow(EntityNotFoundException::new);
        var roster = repository.findRosterByEventUniqueId(moving.getEventUniqueId());
        var index = indexOf(roster, registrationId);
        var target = index + direction;
        if (index < 0 || target < 0 || target >= roster.size()) {
            return;
        }
        var neighbour = roster.get(target);
        var movingOrder = moving.getOrderBy();
        moving.setOrderBy(neighbour.getOrderBy());
        neighbour.setOrderBy(movingOrder);
        repository.save(neighbour);
        repository.save(moving);
    }

    private static int indexOf(List<WorkspaceEventRegistrationEntity> roster, UniqueId registrationId) {
        for (var i = 0; i < roster.size(); i++) {
            if (registrationId.equals(new UniqueId(roster.get(i).getUniqueId()))) {
                return i;
            }
        }
        return -1;
    }

    private int nextOrderBy(UniqueId eventId) {
        return repository.findLastByEventUniqueId(eventId)
                .map(last -> last.getOrderBy() + 1)
                .orElse(0);
    }

    private Map<UniqueId, String> labelsByParticipant(UniqueId workspaceId) {
        // A plain map rather than Collectors.toMap: a label may legitimately be null (a participant with
        // no descriptor), and toMap throws on a null value.
        var labels = new HashMap<UniqueId, String>();
        for (var participant : participantRepository.findByWorkspaceUniqueId(workspaceId)) {
            if (participant.getUniqueId() != null) {
                labels.putIfAbsent(new UniqueId(participant.getUniqueId()), labelOf(participant));
            }
        }
        return labels;
    }

    private static String labelOf(WorkspaceParticipantEntity participant) {
        var descriptor = participant.getDescriptor();
        return descriptor == null ? null : descriptor.label();
    }
}
