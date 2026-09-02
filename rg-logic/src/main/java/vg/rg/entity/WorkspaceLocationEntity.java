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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import static vg.utils.HibernateHelper.effectiveClass;

/**
 * A place saved inside a workspace — the only kind of location that exists. Replaces the former global
 * collection, whose table is retained but unused.
 *
 * <p>{@code workspaceUniqueId} is <strong>not null</strong> and never updated, so a location without a
 * workspace is unrepresentable and a location cannot move between workspaces. It serves two purposes:
 * it scopes every query by construction, and it is the containment link the authority check joins
 * through to find the owning workspace.
 *
 * <p>Coordinates remain optional — a location may be saved without them when Google Maps is
 * unavailable. {@code author}/{@code lastEditor} are for auditing only, never for access control.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_workspace_location")
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceLocationEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    @Column(name = "workspace_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId workspaceUniqueId;

    // Coordinates are optional: a location may be added without them when Google Maps is unavailable.
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(length = 2048)
    private String description;

    @Column(name = "google_place_id", length = 512)
    private String googlePlaceId;

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
        WorkspaceLocationEntity that = (WorkspaceLocationEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
