package vg.rg.service.workspace.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import vg.rg.BaseFuncTest;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceParticipantService;
import vg.rg.service.workspace.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

/**
 * DB-backed coverage of {@link WorkspaceEventRegistrationService} against MySQL, through the real
 * authority boundary and the real workspace-scope joins.
 *
 * <p>The owner holds nothing but the app-wide {@code workspace:owner} gate — no
 * {@code workspace-event*} capability anywhere — which is what demonstrates that owning the workspace is
 * by itself sufficient inside it, and that adding this type required no change to the authority model.
 */
class WorkspaceEventRegistrationServiceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = nextUniqueId();
    private static final UniqueId STRANGER = nextUniqueId();

    @Autowired
    WorkspaceService workspaceService;
    @Autowired
    WorkspaceLocationService locationService;
    @Autowired
    WorkspaceParticipantService participantService;
    @Autowired
    WorkspaceEventService eventService;
    @Autowired
    WorkspaceEventRegistrationService registrationService;
    @Autowired
    WorkspaceEventRegistrationRepository registrationRepository;
    @Autowired
    WorkspaceEventRepository eventRepository;
    @Autowired
    WorkspaceLocationRepository locationRepository;
    @Autowired
    WorkspaceParticipantRepository participantRepository;
    @Autowired
    WorkspaceRepository workspaceRepository;

    private UniqueId workspaceA;
    private UniqueId eventA;
    private WorkspaceParticipantModel alice;
    private WorkspaceParticipantModel bob;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        workspaceA = workspaceService.create(WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        var location = locationService.create(workspaceA, LocationModel.builder().name("Courts").build());
        eventA = eventService.create(workspaceA, WorkspaceEventModel.builder().title("Padel")
                .startAt(Instant.parse("2026-09-13T09:00:00Z")).eventType(WorkspaceEventType.PADEL)
                .locationUniqueId(location.getUniqueId()).maxParticipantCount(2).build()).getUniqueId();
        alice = participantService.register(workspaceA, ParticipantDescriptor.of("Alice", null));
        bob = participantService.register(workspaceA, ParticipantDescriptor.of("Bob", null));
    }

    @AfterEach
    void cleanUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        participantRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void registrations_appendInOrderAndCarryLabels() {
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        var roster = registrationService.list(eventA);
        assertThat(roster).extracting(WorkspaceEventRegistrationModel::getParticipantLabel)
                .containsExactly("Alice", "Bob");
        assertThat(roster).extracting(WorkspaceEventRegistrationModel::getOrderBy)
                .containsExactly(0, 1);
    }

    @Test
    void registration_allowsOverBookingPastTheMaximum() {
        var carol = participantService.register(workspaceA, ParticipantDescriptor.of("Carol", null));
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());
        registrationService.register(eventA, carol.getUniqueId());

        assertThat(registrationService.list(eventA)).hasSize(3);
    }

    @Test
    void registeringTheSameParticipantTwice_isRejected() {
        registrationService.register(eventA, alice.getUniqueId());

        assertThatThrownBy(() -> registrationService.register(eventA, alice.getUniqueId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveDown_reordersTheRoster() {
        var first = registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        registrationService.moveDown(first.getUniqueId());

        assertThat(registrationService.list(eventA))
                .extracting(WorkspaceEventRegistrationModel::getParticipantLabel)
                .containsExactly("Bob", "Alice");
    }

    @Test
    void unregister_removesTheRegistration() {
        var first = registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        registrationService.unregister(first.getUniqueId());

        assertThat(registrationService.list(eventA))
                .extracting(WorkspaceEventRegistrationModel::getParticipantLabel)
                .containsExactly("Bob");
    }

    @Test
    void countByEvents_reportsPerEventTotals() {
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        Map<UniqueId, Long> counts = registrationService.countByEvents(workspaceA, java.util.List.of(eventA));
        assertThat(counts).containsEntry(eventA, 2L);
    }

    @Test
    void stranger_isDenied() {
        var registration = registrationService.register(eventA, alice.getUniqueId());
        authenticate(STRANGER);

        assertThatThrownBy(() -> registrationService.register(eventA, bob.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> registrationService.list(eventA))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> registrationService.unregister(registration.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletingAnEvent_removesItsRegistrations() {
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        eventService.delete(eventA);

        assertThat(registrationRepository.countByEventUniqueId(eventA)).isZero();
    }

    @Test
    void deletingAParticipant_removesItsRegistrations() {
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        participantService.delete(alice.getUniqueId());

        assertThat(registrationService.list(eventA))
                .extracting(WorkspaceEventRegistrationModel::getParticipantLabel)
                .containsExactly("Bob");
    }

    @Test
    void deletingTheWorkspace_removesAllRegistrations() {
        registrationService.register(eventA, alice.getUniqueId());
        registrationService.register(eventA, bob.getUniqueId());

        workspaceService.delete(workspaceA);

        assertThat(registrationRepository.countByWorkspaceUniqueId(workspaceA)).isZero();
    }
}
