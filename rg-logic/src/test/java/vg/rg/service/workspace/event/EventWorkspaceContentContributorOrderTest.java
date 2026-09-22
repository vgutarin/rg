package vg.rg.service.workspace.event;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import vg.rg.service.workspace.LocationWorkspaceContentContributor;
import vg.rg.service.workspace.WorkspaceContentContributor;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkspaceContentContributorOrderTest {

    @Test
    void eventDependencies_areDeletedBeforeTheEventAndItsDirectWorkspaceSiblings() {
        assertThat(EventWorkspaceContentContributor.ORDER)
                .isEqualTo(LocationWorkspaceContentContributor.ORDER - 1);
        assertThat(EventRegistrationWorkspaceContentContributor.ORDER)
                .isEqualTo(Math.min(EventWorkspaceContentContributor.ORDER,
                        WorkspaceContentContributor.DIRECT_WORKSPACE_CHILD_ORDER) - 1);
        assertThat(EventWorkspaceContentContributor.class.getAnnotation(Order.class).value())
                .isEqualTo(EventWorkspaceContentContributor.ORDER);
        assertThat(EventRegistrationWorkspaceContentContributor.class.getAnnotation(Order.class).value())
                .isEqualTo(EventRegistrationWorkspaceContentContributor.ORDER);
    }
}
