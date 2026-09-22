package vg.rg.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** Owner-visible membership request; its descriptor is exposed only to the owning workspace. */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceMembershipRequestModel implements Identifiable {
    private UniqueId uniqueId;
    private UniqueId workspaceUniqueId;
    private UniqueId pendingUserUniqueId;
    private UniqueId originEventUniqueId;
    private ParticipantDescriptor descriptor;
    private Instant createdAt;
    private int version;
}
