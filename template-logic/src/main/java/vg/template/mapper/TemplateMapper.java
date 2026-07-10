package vg.template.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import vg.template.entity.TemplateEntity;
import vg.template.model.TemplateModel;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface TemplateMapper {
    TemplateModel toModel(TemplateEntity src);

    TemplateEntity toEntity(TemplateModel src);

    void updateEntity(@MappingTarget TemplateEntity entity, TemplateModel model);

    void updateModel(@MappingTarget TemplateModel model, TemplateEntity entity);


}
