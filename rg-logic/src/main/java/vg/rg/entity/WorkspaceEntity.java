package vg.rg.entity;

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
 * A named container a single user works inside. Everything a workspace contains derives its access from
 * the workspace: owning it grants complete authority over its contents at any depth.
 *
 * <p>{@code ownerUniqueId} is the scope authority and is the one identity field used for access.
 * {@code author}/{@code lastEditor} are abstract user identities recorded for auditing only, never for
 * access control, and are never mapped to a natural person.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    /**
     * Nullable on purpose. A workspace the <em>system</em> created stores no name, because a stored name
     * cannot follow the viewer's locale; null means "render the localized label". A user-supplied name is
     * stored here and displayed verbatim in every locale from then on.
     */
    @Column(length = 512)
    private String name;

    @Column(length = 2048)
    private String description;

    @Column(name = "owner_unique_id", nullable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId ownerUniqueId;

    /**
     * Set to {@link #ownerUniqueId} on the owner's default workspace, null on every other. The unique
     * index on this column permits at most one default per owner while allowing any number of
     * non-default workspaces, because MySQL unique indexes ignore nulls — which closes the concurrent
     * provisioning race in the database rather than in application code.
     */
    @Column(name = "default_for_owner")
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId defaultForOwner;

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

    /** Whether this workspace is its owner's non-removable default. */
    public boolean isDefaultWorkspace() {
        return defaultForOwner != null;
    }

    /** Whether the label must be rendered from a message key rather than from stored text. */
    public boolean isSystemNamed() {
        return name == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        WorkspaceEntity that = (WorkspaceEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
