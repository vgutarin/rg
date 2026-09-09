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
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.unique.id.jpa.UniqueIdEntity;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * A person a workspace owner registered who has not authenticated and may never do so.
 *
 * <p><strong>A record, not a member.</strong> Nothing here grants access to anything: the row is content
 * of the workspace, exactly as a location is, and the owner's authority over it comes from owning the
 * workspace. That is why adding this type changed no access rule.
 *
 * <p>{@code workspaceUniqueId} is <strong>not null</strong> and never updated, as on
 * {@link WorkspaceLocationEntity}: it scopes every query by construction and is the containment link the
 * authority check joins through.
 *
 * <p><strong>Two identifiers, and one rule that keeps them cheap.</strong> {@code uniqueId} is minted
 * here and is the only identifier other rg content ever references. {@code userUniqueId} is the identity
 * subject, filled in if this person ever redeems an invite, and is deliberately <em>never</em> referenced
 * by rg content — which is what stops a later identity binding from having to rewrite references.
 *
 * <p>{@code descriptor} holds the label and phone number, encrypted; see {@link ParticipantDescriptor}
 * and {@code specs/current/encryption.md}. It is excluded from {@code toString()} because one log line
 * printing an entity would defeat the entire design.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace_participant")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceParticipantEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(name = "workspace_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId workspaceUniqueId;

    // Null until this person authenticates and redeems an invite; some participants never will.
    @Column(name = "user_unique_id")
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId userUniqueId;

    @ToString.Exclude
    @Column(name = "descriptor")
    @Convert(converter = ParticipantDescriptorConverter.class)
    private ParticipantDescriptor descriptor;

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
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        WorkspaceParticipantEntity that = (WorkspaceParticipantEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
