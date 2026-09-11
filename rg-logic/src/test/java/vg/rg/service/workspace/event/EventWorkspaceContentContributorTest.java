package vg.rg.service.workspace.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.repository.workspace.WorkspaceEventRepository;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class EventWorkspaceContentContributorTest {

    private static final UniqueId WORKSPACE = nextUniqueId();

    @Mock WorkspaceEventRepository repository;

    @Test
    void resourceType_identifiesWorkspaceEvents() {
        assertThat(contributor().resourceType()).isEqualTo("WORKSPACE_EVENT");
    }

    @Test
    void deleteAllInWorkspace_countsThenDeletesOnlyTheGivenWorkspace() {
        when(repository.countByWorkspaceUniqueId(WORKSPACE)).thenReturn(3L);

        contributor().deleteAllInWorkspace(WORKSPACE);

        InOrder order = inOrder(repository);
        order.verify(repository).countByWorkspaceUniqueId(WORKSPACE);
        order.verify(repository).deleteByWorkspaceUniqueId(WORKSPACE);
    }

    @Test
    void deleteAllInWorkspace_deletesEvenWhenTheWorkspaceHasNoEvents() {
        when(repository.countByWorkspaceUniqueId(WORKSPACE)).thenReturn(0L);

        contributor().deleteAllInWorkspace(WORKSPACE);

        verify(repository).deleteByWorkspaceUniqueId(WORKSPACE);
    }

    @Test
    void deleteAllInWorkspace_rejectsNullBeforeTouchingPersistence() {
        assertThatThrownBy(() -> contributor().deleteAllInWorkspace(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workspaceId");

        verify(repository, never()).countByWorkspaceUniqueId(WORKSPACE);
        verify(repository, never()).deleteByWorkspaceUniqueId(WORKSPACE);
    }

    private EventWorkspaceContentContributor contributor() {
        return new EventWorkspaceContentContributor(repository);
    }
}
