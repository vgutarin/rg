package vg.rg.entity.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vg.rg.model.resource.ResourceVisibilityEffect;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;
import vg.unique.id.jpa.UniqueIdEntity;
import vg.unique.id.jpa.UniqueIdLongConverter;
import vg.unique.id.model.UniqueId;

/** A typed subject rule belonging to one generic resource publication. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "rg_resource_visibility_rule")
public class ResourceVisibilityRuleEntity implements UniqueIdEntity {

    @Id
    private Long uniqueId;

    @Column(name = "resource_unique_id", nullable = false, updatable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId resourceUniqueId;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private ResourceVisibilityEffect effect;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "subject_kind", nullable = false)
    private ResourceVisibilitySubjectKind subjectKind;

    @Column(name = "subject_unique_id", nullable = false)
    @Convert(converter = UniqueIdLongConverter.class)
    private UniqueId subjectUniqueId;
}
