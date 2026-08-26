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
 * A place saved in the shared collection. Coordinates are the primary match key (not a uniqueness
 * constraint). {@code author}/{@code lastEditor} are abstract user identities recorded for auditing
 * only (never for access control) and are never mapped to a natural person.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table(name = "rg_location")
@EntityListeners(AuditingEntityListener.class)
public class LocationEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Version
    private int version;

    // Coordinates are optional: a location may be added without them when Google Maps is unavailable.
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private String name;

    @Column(length = 1024)
    private String description;

    @Column(name = "google_place_id", length = 512)
    private String googlePlaceId;

    @CreatedBy
    @Column(updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId author;

    @LastModifiedBy
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId lastEditor;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        LocationEntity that = (LocationEntity) o;
        return getUniqueId() != null && Objects.equals(getUniqueId(), that.getUniqueId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
