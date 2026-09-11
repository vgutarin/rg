package vg.rg.service.workspace.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vg.rg.BaseFuncTest;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

class WorkspaceEventServiceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId STRANGER = nextUniqueId();

    @Autowired
    WorkspaceService workspaceService;
    @Autowired
    WorkspaceEventService eventService;
    @Autowired
    WorkspaceEventRepository eventRepository;
    @Autowired
    WorkspaceLocationRepository locationRepository;
    @Autowired
    WorkspaceRepository workspaceRepository;
    @Autowired
    WorkspaceLocationService locationService;

    private UniqueId workspaceA;
    private UniqueId workspaceB;
    private LocationModel locationA;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        workspaceA = workspaceService.create(WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        workspaceB = workspaceService.create(WorkspaceModel.builder().name("Beta").build()).getUniqueId();
        locationA = locationService.create(workspaceA, LocationModel.builder().name("Alpha courts").build());
    }

    @AfterEach
    void cleanUp() {
        eventRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void event_isScopedToOneWorkspaceAndItsPublicationStateCanChange() {
        var created = eventService.create(workspaceA, WorkspaceEventModel.builder()
                .title("Planning").startAt(Instant.parse("2026-09-13T09:00:00Z"))
                .eventType(WorkspaceEventType.PADEL).maxParticipantCount(8)
                .locationUniqueId(locationA.getUniqueId()).isPublished(false).build());

        assertThat(eventService.browse(workspaceA, "plan", PageRequest.of(0, 10)).getContent())
                .extracting(WorkspaceEventModel::getTitle).containsExactly("Planning");
        assertThat(eventService.browse(workspaceB, null, PageRequest.of(0, 10)).getContent()).isEmpty();

        var updated = eventService.update(WorkspaceEventModel.builder().uniqueId(created.getUniqueId())
                .version(created.getVersion()).title("Published planning").startAt(created.getStartAt())
                .eventType(created.getEventType()).maxParticipantCount(created.getMaxParticipantCount())
                .locationUniqueId(created.getLocationUniqueId())
                .isPublished(true).build());
        assertThat(updated.isPublished()).isTrue();
        assertThat(updated.getTitle()).isEqualTo("Published planning");
        assertThat(updated.getStartAt()).isEqualTo(Instant.parse("2026-09-13T09:00:00Z"));
        assertThat(updated.getEventType()).isEqualTo(WorkspaceEventType.PADEL);
        assertThat(updated.getMaxParticipantCount()).isEqualTo(8);
    }

    @Test
    void stranger_isDeniedAgainstBothTheContainerAndEvent() {
        var created = eventService.create(workspaceA, WorkspaceEventModel.builder().title("Planning")
                .startAt(Instant.parse("2026-09-13T09:00:00Z")).eventType(WorkspaceEventType.PADEL)
                .maxParticipantCount(8).locationUniqueId(locationA.getUniqueId()).build());
        authenticate(STRANGER);

        assertThatThrownBy(() -> eventService.browse(workspaceA, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> eventService.update(WorkspaceEventModel.builder()
                .uniqueId(created.getUniqueId()).version(created.getVersion()).title("Changed").build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requiredLocationAndOptionalEndTime_arePersisted() {
        var location = locationService.create(workspaceA, LocationModel.builder().name("Central courts").build());
        var startAt = Instant.parse("2026-09-13T09:00:00Z");
        var endAt = Instant.parse("2026-09-13T10:30:00Z");

        var created = eventService.create(workspaceA, WorkspaceEventModel.builder().title("Morning Padel")
                .startAt(startAt).endAt(endAt).eventType(WorkspaceEventType.PADEL)
                .locationUniqueId(location.getUniqueId()).maxParticipantCount(12).build());

        assertThat(created.getStartAt()).isEqualTo(startAt);
        assertThat(created.getEndAt()).isEqualTo(endAt);
        assertThat(created.getLocationUniqueId()).isEqualTo(location.getUniqueId());
        assertThat(created.getEventType()).isEqualTo(WorkspaceEventType.PADEL);
        assertThat(created.getMaxParticipantCount()).isEqualTo(12);

    }

}
