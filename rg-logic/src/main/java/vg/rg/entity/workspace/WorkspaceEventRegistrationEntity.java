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
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.unique.id.jpa.UniqueIdEntity;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * One participant registered to one event, owned by the event's workspace.
 *
 * <p>A join row, and content of the workspace exactly as an event or a participant is: the owner's
 * authority over it comes from owning the workspace, so adding this type changed no access rule.
 *
 * <p>{@code eventUniqueId} and {@code participantUniqueId} reference their subjects by the identifier
 * minted for each, and both are not null and never updated. The owning workspace is <strong>not</strong>
 * stored here: unlike a direct child of the workspace, a join row's workspace is derivable, so the
 * authority check and cascade cleanup join through {@link WorkspaceEventEntity} instead. {@code orderBy}
 * is the participant's position within the event's roster; a database unique constraint on
 * {@code (event_unique_id, participant_unique_id)} keeps a participant registered to an event at most
 * once.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace_event_registration")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceEventRegistrationEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(name = "event_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId eventUniqueId;

    @Column(name = "participant_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId participantUniqueId;

    @Column(name = "order_by", nullable = false)
    private int orderBy;

    @CreatedBy
    @Column(updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId author;

    @LastModifiedBy
    @Column(name = "last_editor")
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId lastEditor;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (effectiveClass(this) != effectiveClass(other)) return false;
        WorkspaceEventRegistrationEntity that = (WorkspaceEventRegistrationEntity) other;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
