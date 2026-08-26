package vg.rg.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import vg.rg.entity.LocationEntity;
import vg.rg.model.LocationModel;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface LocationMapper {

    LocationModel toModel(LocationEntity src);

    LocationEntity toEntity(LocationModel src);

    void updateEntity(@MappingTarget LocationEntity entity, LocationModel model);

    void updateModel(@MappingTarget LocationModel model, LocationEntity entity);
}
