package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.repository.workspace.WorkspaceSelectionRepository;
import vg.unique.id.model.UniqueId;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextUniqueId;

/**
 * The active-workspace selection against MySQL: that it survives the end of a session, that it is per
 * user rather than per device, and that it repairs itself rather than erroring.
 *
 * <p>Clearing the security context and re-authenticating stands in for signing out and back in — the
 * selection lives in the database keyed by the user's abstract identity, so nothing session-scoped
 * carries it.
 */
class WorkspaceSelectionFuncTest extends BaseFuncTest {

    private static final UniqueId USER = nextUniqueId();
    private static final UniqueId OTHER_USER = nextUniqueId();

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private WorkspaceSelectionService selectionService;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private WorkspaceLocationRepository locationRepository;
    @Autowired
    private WorkspaceSelectionRepository selectionRepository;

    @BeforeEach
    void setUp() {
        authenticate(USER);
    }

    @AfterEach
    void cleanUp() {
        selectionRepository.deleteAll();
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void firstEntry_provisionsTheDefaultAndMakesItActive() {
        var active = selectionService.activeWorkspace();

        assertThat(active.isDefaultWorkspace()).isTrue();
        // No stored name: a system-created workspace's label is resolved per viewer instead.
        assertThat(active.getName()).isNull();
        assertThat(active.isSystemNamed()).isTrue();
    }

    @Test
    void selection_survivesTheEndOfASession() {
        var second = workspaceService.create(WorkspaceModel.builder().name("Second").build());
        selectionService.select(second.getUniqueId());

        // Sign out and back in.
        SecurityContextHolder.clearContext();
        authenticate(USER);

        assertThat(selectionService.activeWorkspace().getUniqueId()).isEqualTo(second.getUniqueId());
    }

    @Test
    void selection_isPerUserNotPerDevice() {
        var mine = workspaceService.create(WorkspaceModel.builder().name("Mine").build());
        selectionService.select(mine.getUniqueId());

        // A different user in the same process must not inherit the selection.
        authenticate(OTHER_USER);
        var theirs = selectionService.activeWorkspace();

        assertThat(theirs.getUniqueId()).isNotEqualTo(mine.getUniqueId());
        assertThat(theirs.isDefaultWorkspace()).isTrue();

        authenticate(USER);
        assertThat(selectionService.activeWorkspace().getUniqueId()).isEqualTo(mine.getUniqueId());
    }

    @Test
    void switching_replacesTheSelectionRatherThanAccumulating() {
        var first = workspaceService.create(WorkspaceModel.builder().name("First").build());
        var second = workspaceService.create(WorkspaceModel.builder().name("Second").build());

        selectionService.select(first.getUniqueId());
        selectionService.select(second.getUniqueId());

        assertThat(selectionService.activeWorkspace().getUniqueId()).isEqualTo(second.getUniqueId());
        // Exactly one row per user, which is what makes "one active workspace" true by construction.
        assertThat(selectionRepository.count()).isEqualTo(1);
    }

    @Test
    void selectingAnotherUsersWorkspace_isDenied() {
        var mine = workspaceService.create(WorkspaceModel.builder().name("Mine").build());

        authenticate(OTHER_USER);

        assertThatThrownBy(() -> selectionService.select(mine.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void selectingAnUnknownWorkspace_isDenied() {
        assertThatThrownBy(() -> selectionService.select(new UniqueId(424242L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletingASelectedWorkspace_isPreventedByTheDatabase() {
        // A dangling selection is not something to repair -- the foreign key makes it unrepresentable.
        // That is a stronger guarantee, and it imposes an ordering constraint on workspace removal:
        // the selection must be repointed BEFORE the workspace row is deleted, or the delete fails.
        var doomed = workspaceService.create(WorkspaceModel.builder().name("Doomed").build());
        selectionService.select(doomed.getUniqueId());

        assertThatThrownBy(() -> {
            workspaceRepository.deleteById(doomed.getUniqueId().getLongValue());
            workspaceRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void absentSelectionRow_fallsBackToTheDefaultAndRepairsThePointer() {
        // The reachable stale case: the row is gone (never written, or cleaned up) while the user's
        // workspaces remain. Falling back must not error, and must leave a usable pointer behind.
        var second = workspaceService.create(WorkspaceModel.builder().name("Second").build());
        selectionService.select(second.getUniqueId());
        selectionRepository.deleteAll();
        selectionRepository.flush();

        var recovered = selectionService.activeWorkspace();

        assertThat(recovered.isDefaultWorkspace()).isTrue();
        assertThat(selectionRepository.findById(USER.getLongValue()))
                .get()
                .extracting(WorkspaceSelectionEntity::getWorkspaceUniqueId)
                .isEqualTo(recovered.getUniqueId());
    }

}
