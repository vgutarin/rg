package vg.rg.entity.resource;

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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** Publication state shared by any published resource. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "rg_resource_publication")
@EntityListeners(AuditingEntityListener.class)
public class ResourcePublicationEntity {

    @Id
    @Column(name = "resource_unique_id")
    private Long resourceUniqueId;

    @Version
    private int version;

    @Column(name = "visible_since")
    private Instant visibleSince;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visibility_mode", nullable = false)
    @Builder.Default
    private ResourceVisibilityMode visibilityMode = ResourceVisibilityMode.PUBLIC;

    @CreatedBy
    @Column(updatable = false, nullable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId author;

    @LastModifiedBy
    @Column(name = "last_editor")
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId lastEditor;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
