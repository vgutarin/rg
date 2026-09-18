package vg.rg.service.workspace.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.entity.workspace.WorkspaceEventEntity;
import vg.rg.entity.workspace.WorkspaceEventRegistrationEntity;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.mapper.workspace.WorkspaceEventRegistrationMapper;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceEventRegistrationServiceImplTest {

    private static final UniqueId WORKSPACE = nextUniqueId();
    private static final UniqueId OTHER_WORKSPACE = nextUniqueId();
    private static final UniqueId EVENT = nextUniqueId();
    private static final UniqueId PARTICIPANT = nextUniqueId();

    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    WorkspaceEventRegistrationRepository repository;
    @Mock
    WorkspaceEventRepository eventRepository;
    @Mock
    WorkspaceParticipantRepository participantRepository;

    private WorkspaceEventRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceEventRegistrationServiceImpl(
                uniqueIdService, repository, eventRepository, participantRepository,
                new WorkspaceEventRegistrationMapper());
        when(repository.saveWithNewUniqueId(any(), any())).thenAnswer(inv -> {
            WorkspaceEventRegistrationEntity entity = inv.getArgument(0);
            entity.setUniqueId(nextUniqueId().getLongValue());
            return entity;
        });
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void register_appendsAfterTheLastPositionAndScopesToTheEventsWorkspace() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event(WORKSPACE)));
        when(participantRepository.findById(PARTICIPANT))
                .thenReturn(Optional.of(participant(WORKSPACE, "Alice")));
        when(repository.existsByEventUniqueIdAndParticipantUniqueId(EVENT, PARTICIPANT)).thenReturn(false);
        when(repository.findLastByEventUniqueId(EVENT))
                .thenReturn(Optional.of(registration(EVENT, 4)));

        var model = service.register(EVENT, PARTICIPANT);

        verify(repository).saveWithNewUniqueId(argThat(entity ->
                EVENT.equals(entity.getEventUniqueId())
                        && PARTICIPANT.equals(entity.getParticipantUniqueId())
                        && entity.getOrderBy() == 5), any());
        assertThat(model.getParticipantLabel()).isEqualTo("Alice");
    }

    @Test
    void register_startsAtZeroForAnEmptyRoster() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event(WORKSPACE)));
        when(participantRepository.findById(PARTICIPANT))
                .thenReturn(Optional.of(participant(WORKSPACE, "Alice")));
        when(repository.existsByEventUniqueIdAndParticipantUniqueId(EVENT, PARTICIPANT)).thenReturn(false);
        when(repository.findLastByEventUniqueId(EVENT))
                .thenReturn(Optional.empty());

        service.register(EVENT, PARTICIPANT);

        verify(repository).saveWithNewUniqueId(argThat(entity -> entity.getOrderBy() == 0), any());
    }

    @Test
    void register_rejectsAParticipantAlreadyRegistered() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event(WORKSPACE)));
        when(participantRepository.findById(PARTICIPANT))
                .thenReturn(Optional.of(participant(WORKSPACE, "Alice")));
        when(repository.existsByEventUniqueIdAndParticipantUniqueId(EVENT, PARTICIPANT)).thenReturn(true);

        assertThatThrownBy(() -> service.register(EVENT, PARTICIPANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventRegistrationServiceImpl.ALREADY_REGISTERED);
        verify(repository, never()).saveWithNewUniqueId(any(), any());
    }

    @Test
    void register_rejectsAParticipantFromAnotherWorkspace() {
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event(WORKSPACE)));
        when(participantRepository.findById(PARTICIPANT))
                .thenReturn(Optional.of(participant(OTHER_WORKSPACE, "Outsider")));

        assertThatThrownBy(() -> service.register(EVENT, PARTICIPANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventRegistrationServiceImpl.PARTICIPANT_NOT_IN_WORKSPACE);
        verify(repository, never()).saveWithNewUniqueId(any(), any());
    }

    @Test
    void register_allowsOverBookingPastTheEventsMaximum() {
        var event = event(WORKSPACE);
        event.setMaxParticipantCount(1);
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event));
        when(participantRepository.findById(PARTICIPANT))
                .thenReturn(Optional.of(participant(WORKSPACE, "Alice")));
        when(repository.existsByEventUniqueIdAndParticipantUniqueId(EVENT, PARTICIPANT)).thenReturn(false);
        when(repository.findLastByEventUniqueId(EVENT))
                .thenReturn(Optional.of(registration(EVENT, 9)));

        service.register(EVENT, PARTICIPANT);

        verify(repository).saveWithNewUniqueId(any(), any());
    }

    @Test
    void list_ordersByOrderByAndResolvesLabels() {
        var first = registration(EVENT, 0);
        first.setParticipantUniqueId(PARTICIPANT);
        var second = registration(EVENT, 1);
        var other = nextUniqueId();
        second.setParticipantUniqueId(other);
        when(repository.findRosterByEventUniqueId(EVENT))
                .thenReturn(List.of(first, second));
        // The workspace comes from the event now, not the registration.
        when(eventRepository.findById(EVENT)).thenReturn(Optional.of(event(WORKSPACE)));
        when(participantRepository.findByWorkspaceUniqueId(WORKSPACE)).thenReturn(List.of(
                participant(PARTICIPANT, WORKSPACE, "Alice"),
                participant(other, WORKSPACE, "Bob")));

        var roster = service.list(EVENT);

        assertThat(roster).extracting(WorkspaceEventRegistrationModel::getParticipantLabel)
                .containsExactly("Alice", "Bob");
    }

    @Test
    void list_isEmptyForAnEventWithNoRoster() {
        when(repository.findRosterByEventUniqueId(EVENT)).thenReturn(List.of());
        assertThat(service.list(EVENT)).isEmpty();
    }

    @Test
    void unregister_deletesTheRegistration() {
        var registrationId = nextUniqueId();
        var entity = registration(EVENT, 3);
        entity.setUniqueId(registrationId.getLongValue());
        when(repository.findById(registrationId)).thenReturn(Optional.of(entity));

        service.unregister(registrationId);

        verify(repository).delete(entity);
    }

    @Test
    void moveUp_swapsOrderByWithThePrecedingRegistration() {
        var top = registration(EVENT, 0);
        var topId = nextUniqueId();
        top.setUniqueId(topId.getLongValue());
        var moving = registration(EVENT, 1);
        var movingId = nextUniqueId();
        moving.setUniqueId(movingId.getLongValue());
        when(repository.findById(movingId)).thenReturn(Optional.of(moving));
        when(repository.findRosterByEventUniqueId(EVENT))
                .thenReturn(List.of(top, moving));

        service.moveUp(movingId);

        assertThat(moving.getOrderBy()).isZero();
        assertThat(top.getOrderBy()).isEqualTo(1);
        verify(repository).save(moving);
        verify(repository).save(top);
    }

    @Test
    void moveUp_isANoOpAtTheTop() {
        var top = registration(EVENT, 0);
        var topId = nextUniqueId();
        top.setUniqueId(topId.getLongValue());
        var other = registration(EVENT, 1);
        other.setUniqueId(nextUniqueId().getLongValue());
        when(repository.findById(topId)).thenReturn(Optional.of(top));
        when(repository.findRosterByEventUniqueId(EVENT))
                .thenReturn(List.of(top, other));

        service.moveUp(topId);

        verify(repository, never()).save(any());
    }

    @Test
    void moveDown_isANoOpAtTheBottom() {
        var top = registration(EVENT, 0);
        top.setUniqueId(nextUniqueId().getLongValue());
        var bottom = registration(EVENT, 1);
        var bottomId = nextUniqueId();
        bottom.setUniqueId(bottomId.getLongValue());
        when(repository.findById(bottomId)).thenReturn(Optional.of(bottom));
        when(repository.findRosterByEventUniqueId(EVENT))
                .thenReturn(List.of(top, bottom));

        service.moveDown(bottomId);

        verify(repository, never()).save(any());
    }

    private static WorkspaceEventEntity event(UniqueId workspace) {
        return WorkspaceEventEntity.builder()
                .uniqueId(EVENT.getLongValue())
                .workspaceUniqueId(workspace)
                .maxParticipantCount(24)
                .build();
    }

    private static WorkspaceEventRegistrationEntity registration(UniqueId event, int orderBy) {
        return WorkspaceEventRegistrationEntity.builder()
                .eventUniqueId(event)
                .orderBy(orderBy)
                .build();
    }

    private static WorkspaceParticipantEntity participant(UniqueId workspace, String label) {
        return participant(PARTICIPANT, workspace, label);
    }

    private static WorkspaceParticipantEntity participant(UniqueId id, UniqueId workspace, String label) {
        return WorkspaceParticipantEntity.builder()
                .uniqueId(id.getLongValue())
                .workspaceUniqueId(workspace)
                .descriptor(ParticipantDescriptor.of(label, null))
                .build();
    }
}
