package vg.rg.entity.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import vg.rg.model.workspace.WorkspaceEventType;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/** A titled event owned by one workspace. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace_event")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceEventEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(name = "workspace_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId workspaceUniqueId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "location_unique_id", nullable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId locationUniqueId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "event_type", nullable = false)
    private WorkspaceEventType eventType;

    @Column(name = "max_participant_count", nullable = false)
    private Integer maxParticipantCount;

    @Column(name = "is_published", nullable = false)
    private boolean isPublished;

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
        WorkspaceEventEntity that = (WorkspaceEventEntity) other;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
