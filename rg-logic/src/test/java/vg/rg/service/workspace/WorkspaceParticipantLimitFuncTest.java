package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import vg.rg.BaseFuncTest;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The roster bound, on its own context with the limit lowered to something a test can reach.
 *
 * <p>Separate from {@link WorkspaceParticipantServiceFuncTest} because the production default is 1024
 * and registering that many rows to observe one refusal would cost far more than the assertion is
 * worth. The cost of this arrangement is one extra Spring context, which is the cheaper side of the
 * trade.
 *
 * <p>The bound is not cosmetic. It is what keeps the index-free duplicate check finite: an encrypted
 * descriptor cannot be compared by the database, so registration opens every existing envelope in the
 * workspace, and the roster is also sorted and filtered in memory for the same reason. Bounding the
 * count is therefore this feature's documented resource-control strategy.
 */
@TestPropertySource(properties = "rg.workspace.participants-max-per-workspace=2")
class WorkspaceParticipantLimitFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(4401L);

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceParticipantService service;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceParticipantRepository participantRepository;

    private UniqueId workspaceA;
    private UniqueId workspaceB;

    @BeforeEach
    void setUp() {
        authenticate();
        workspaceA = workspaceService.create(WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        workspaceB = workspaceService.create(WorkspaceModel.builder().name("Beta").build()).getUniqueId();
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void registeringPastTheBound_isRefusedAndLeavesTheRosterUsable() {
        service.register(workspaceA, ParticipantDescriptor.of("First", null));
        service.register(workspaceA, ParticipantDescriptor.of("Second", null));

        assertThatThrownBy(() -> service.register(workspaceA, ParticipantDescriptor.of("Third", null)))
                .isInstanceOf(ParticipantLimitReachedException.class)
                .hasMessage(ParticipantLimitReachedException.MESSAGE_KEY)
                .extracting(exception -> ((ParticipantLimitReachedException) exception).limit())
                .isEqualTo(2);

        // A refusal must not damage what is already there — the same guarantee the workspace count
        // bound gives.
        assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent()).hasSize(2);
    }

    /** The bound is per workspace, so one full roster must not block another workspace. */
    @Test
    void aFullRoster_doesNotBlockAnotherWorkspace() {
        service.register(workspaceA, ParticipantDescriptor.of("First", null));
        service.register(workspaceA, ParticipantDescriptor.of("Second", null));

        assertThat(service.register(workspaceB, ParticipantDescriptor.of("Elsewhere", null))
                .getUniqueId()).isNotNull();
    }

    /** Removing someone frees a slot: the check counts current rows rather than rows ever created. */
    @Test
    void removingAParticipant_freesASlot() {
        service.register(workspaceA, ParticipantDescriptor.of("First", null));
        var second = service.register(workspaceA, ParticipantDescriptor.of("Second", null));

        service.delete(second.getUniqueId());

        assertThat(service.register(workspaceA, ParticipantDescriptor.of("Third", null))
                .getUniqueId()).isNotNull();
    }

    private static void authenticate() {
        var principal = new AuthenticatedUserPrincipal(
                OWNER, "Test User",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
