package vg.rg.service.workspace;

import vg.rg.model.workspace.WorkspaceMembershipRequestModel;
import vg.rg.model.workspace.WorkspaceMembershipApprovalTarget;
import vg.unique.id.model.UniqueId;

import java.util.List;

/** User-initiated request and owner-controlled approval flow for workspace membership. */
public interface WorkspaceMembershipRequestService {
    void requestJoin(UniqueId eventId);
    List<WorkspaceMembershipRequestModel> pending(UniqueId workspaceId);
    void approve(UniqueId workspaceId, UniqueId requestId, WorkspaceMembershipApprovalTarget target,
                 UniqueId existingParticipantId, String label);
    void reject(UniqueId workspaceId, UniqueId requestId);
}
