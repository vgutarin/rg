package vg.rg.service.workspace.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.entity.workspace.WorkspaceEventEntity;
import vg.rg.mapper.workspace.WorkspaceEventMapper;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceEventServiceImplTest {

    private static final UniqueId WORKSPACE = nextUniqueId();
    private static final UniqueId EVENT = nextUniqueId();
    private static final UniqueId LOCATION = nextUniqueId();

    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    WorkspaceEventRepository repository;
    @Mock
    WorkspaceEventRegistrationRepository registrationRepository;
    @Mock
    WorkspaceLocationRepository locationRepository;
    @Mock
    WorkspaceEventMapper mapper;

    private WorkspaceEventService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceEventServiceImpl(
                uniqueIdService, repository, registrationRepository, locationRepository, mapper);
        when(repository.saveWithNewUniqueId(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toModel(any())).thenReturn(WorkspaceEventModel.builder().build());
        when(locationRepository.existsByUniqueIdAndWorkspaceUniqueId(LOCATION.getLongValue(), WORKSPACE))
                .thenReturn(true);
    }

    @Test
    void create_scopesAndNormalizesTheEvent() {
        var startAt = Instant.parse("2026-09-13T09:00:00Z");
        var endAt = Instant.parse("2026-09-13T10:30:00Z");
        service.create(WORKSPACE, event("  Planning  ").startAt(startAt).endAt(endAt).isPublished(true).build());

        verify(repository).saveWithNewUniqueId(argThat(event -> WORKSPACE.equals(event.getWorkspaceUniqueId())
                && "Planning".equals(event.getTitle()) && startAt.equals(event.getStartAt())
                && endAt.equals(event.getEndAt()) && event.getEventType() == WorkspaceEventType.PADEL
                && event.getMaxParticipantCount() == 12
                && event.isPublished()), eq(uniqueIdService));
    }

    @Test
    void blankTitle_isRejected() {
        assertThatThrownBy(() -> service.create(WORKSPACE, event(" ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.TITLE_REQUIRED);
    }

    @Test
    void missingStartOrType_isRejected() {
        assertThatThrownBy(() -> service.create(WORKSPACE, WorkspaceEventModel.builder()
                .title("Planning").eventType(WorkspaceEventType.PADEL).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.START_REQUIRED);
        assertThatThrownBy(() -> service.create(WORKSPACE, WorkspaceEventModel.builder()
                .title("Planning").startAt(Instant.parse("2026-09-13T09:00:00Z")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.TYPE_REQUIRED);
    }

    @Test
    void missingOrNonPositiveMaxParticipantCount_isRejected() {
        assertThatThrownBy(() -> service.create(WORKSPACE, event("Planning").maxParticipantCount(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.MAX_PARTICIPANT_COUNT_REQUIRED);
        assertThatThrownBy(() -> service.create(WORKSPACE, event("Planning").maxParticipantCount(0).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.MAX_PARTICIPANT_COUNT_NOT_POSITIVE);
    }

    @Test
    void endBeforeStart_isRejected() {
        assertThatThrownBy(() -> service.create(WORKSPACE, event("Planning")
                .startAt(Instant.parse("2026-09-13T10:00:00Z"))
                .endAt(Instant.parse("2026-09-13T09:00:00Z")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.END_BEFORE_START);
    }

    @Test
    void locationMustBelongToTheEventWorkspace() {
        var location = nextUniqueId();
        when(locationRepository.existsByUniqueIdAndWorkspaceUniqueId(location.getLongValue(), WORKSPACE)).thenReturn(false);

        assertThatThrownBy(() -> service.create(WORKSPACE, event("Planning").locationUniqueId(location).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.LOCATION_OUTSIDE_WORKSPACE);
    }

    @Test
    void locationIsRequired() {
        assertThatThrownBy(() -> service.create(WORKSPACE, event("Planning").locationUniqueId(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(WorkspaceEventServiceImpl.LOCATION_REQUIRED);
    }

    @Test
    void update_replacesTheScheduleTypeAndOptionalLocation() {
        var location = nextUniqueId();
        var stored = WorkspaceEventEntity.builder().uniqueId(EVENT.getLongValue()).workspaceUniqueId(WORKSPACE)
                .version(1).title("Before").build();
        when(repository.findById(EVENT)).thenReturn(Optional.of(stored));
        when(locationRepository.existsByUniqueIdAndWorkspaceUniqueId(location.getLongValue(), WORKSPACE)).thenReturn(true);

        service.update(event("After").uniqueId(EVENT).version(1)
                .startAt(Instant.parse("2026-09-13T11:00:00Z"))
                .endAt(Instant.parse("2026-09-13T12:30:00Z"))
                .eventType(WorkspaceEventType.TENNIS).locationUniqueId(location).maxParticipantCount(16).build());

        verify(repository).save(argThat(event -> "After".equals(event.getTitle())
                && Instant.parse("2026-09-13T11:00:00Z").equals(event.getStartAt())
                && Instant.parse("2026-09-13T12:30:00Z").equals(event.getEndAt())
                && event.getEventType() == WorkspaceEventType.TENNIS
                && event.getMaxParticipantCount() == 16
                && location.equals(event.getLocationUniqueId())));
    }

    @Test
    void staleUpdate_isRejectedWithoutWriting() {
        when(repository.findById(EVENT)).thenReturn(Optional.of(WorkspaceEventEntity.builder()
                .uniqueId(EVENT.getLongValue()).version(2).title("Before").build()));

        assertThatThrownBy(() -> service.update(event("After")
                .uniqueId(EVENT).version(1).build()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void browse_trimsTheFilterAndKeepsTheWorkspaceScope() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByWorkspaceUniqueIdAndTitleContainingIgnoreCase(WORKSPACE, "planning", pageable))
                .thenReturn(Page.empty());

        service.browse(WORKSPACE, " planning ", pageable);

        verify(repository).findByWorkspaceUniqueIdAndTitleContainingIgnoreCase(WORKSPACE, "planning", pageable);
    }

    private static WorkspaceEventModel.WorkspaceEventModelBuilder event(String title) {
        return WorkspaceEventModel.builder()
                .title(title)
                .startAt(Instant.parse("2026-09-13T09:00:00Z"))
                .eventType(WorkspaceEventType.PADEL)
                .maxParticipantCount(12)
                .locationUniqueId(LOCATION);
    }
}
