package vg.rg.entity.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * Which workspace a user is currently working in. Exactly one row per user — the primary key <em>is</em>
 * that rule — and it is stored server-side rather than per device, so the selection follows the user
 * wherever they sign in.
 *
 * <p>Deliberately not a contained resource: it is a per-user pointer, so it registers no workspace-scope
 * provider and is never addressable by an authority check. It also intentionally does not implement
 * {@code UniqueIdEntity}, because its key is the user's identity rather than an identifier of its own.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace_selection")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceSelectionEntity {

    @Id
    @Column(name = "user_unique_id")
    private Long userUniqueId;

    @Column(name = "workspace_unique_id", nullable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId workspaceUniqueId;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        WorkspaceSelectionEntity that = (WorkspaceSelectionEntity) o;
        return userUniqueId != null && Objects.equals(userUniqueId, that.userUniqueId);
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
