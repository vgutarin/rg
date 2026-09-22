package vg.rg.entity.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.unique.id.jpa.UniqueIdEntity;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** Encrypted requester descriptor for a pending workspace membership request. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "rg_workspace_membership_request")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceMembershipRequestEntity implements UniqueIdEntity {
    @Id
    private Long uniqueId;
    @Version
    private int version;
    @Column(name = "workspace_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId workspaceUniqueId;
    @Column(name = "pending_user_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId pendingUserUniqueId;
    @Column(name = "origin_event_unique_id")
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId originEventUniqueId;
    @Column(name = "descriptor")
    @Convert(converter = ParticipantDescriptorConverter.class)
    private ParticipantDescriptor descriptor;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
