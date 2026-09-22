package vg.rg.repository.resource;

import vg.rg.entity.resource.ResourceVisibilityRuleEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;
import vg.unique.id.model.UniqueId;

import java.util.Collection;
import java.util.List;

/** Persistence for audience rules attached to generic resource publications. */
public interface ResourceVisibilityRuleRepository extends UniqueIdJpaRepository<ResourceVisibilityRuleEntity> {

    List<ResourceVisibilityRuleEntity> findByResourceUniqueId(UniqueId resourceUniqueId);

    void deleteByResourceUniqueId(UniqueId resourceUniqueId);

    void deleteByResourceUniqueIdIn(Collection<UniqueId> resourceUniqueIds);
}
