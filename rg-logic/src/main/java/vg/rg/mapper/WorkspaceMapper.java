package vg.rg.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vg.rg.entity.WorkspaceEntity;
import vg.rg.model.WorkspaceModel;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface WorkspaceMapper {

    /**
     * {@code defaultWorkspace} is derived from whether the entity carries its owner's default marker.
     * Owner and audit identities are not mapped: they are scope and audit data, not presentation input.
     */
    @Mapping(target = "defaultWorkspace", expression = "java(src.isDefaultWorkspace())")
    WorkspaceModel toModel(WorkspaceEntity src);
}
