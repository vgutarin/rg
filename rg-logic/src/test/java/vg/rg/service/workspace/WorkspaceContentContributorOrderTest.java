package vg.rg.service.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import vg.rg.service.workspace.event.EventWorkspaceContentContributor;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceContentContributorOrderTest {

    @Test
    void directWorkspaceChildrenWithoutEventDependencies_areOrderedImmediatelyBeforeTheirParent() {
        assertThat(WorkspaceContentContributor.ORDER).isEqualTo(10);
        assertThat(LocationWorkspaceContentContributor.ORDER)
                .isEqualTo(WorkspaceContentContributor.ORDER - 1);
        assertThat(ParticipantWorkspaceContentContributor.ORDER)
                .isEqualTo(WorkspaceContentContributor.ORDER - 1);
        assertThat(LocationWorkspaceContentContributor.class.getAnnotation(Order.class).value())
                .isEqualTo(LocationWorkspaceContentContributor.ORDER);
        assertThat(ParticipantWorkspaceContentContributor.class.getAnnotation(Order.class).value())
                .isEqualTo(ParticipantWorkspaceContentContributor.ORDER);
    }

    @Test
    void membershipRequests_areOrderedBelowTheirLowestOrderParent() {
        assertThat(MembershipRequestWorkspaceContentContributor.ORDER)
                .isEqualTo(Math.min(WorkspaceContentContributor.ORDER,
                        EventWorkspaceContentContributor.ORDER) - 1);
        assertThat(MembershipRequestWorkspaceContentContributor.class.getAnnotation(Order.class).value())
                .isEqualTo(MembershipRequestWorkspaceContentContributor.ORDER);
    }
}
