package vg.rg.mapper.workspace;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vg.rg.entity.workspace.WorkspaceLocationEntity;
import vg.rg.model.geo.LocationModel;
import vg.unique.id.mapper.UniqueIdMapper;

/**
 * Maps workspace-scoped locations onto the existing {@link LocationModel}, which is reused unchanged.
 *
 * <p>Separate from {@code LocationMapper} rather than a retarget of it, because the global location
 * service still maps the retired entity. The two merge when the global scope is retired.
 *
 * <p>{@code workspaceUniqueId} is deliberately not on the model, so the scope is a parameter of the
 * operation rather than editable content — which is what makes a location's workspace unforgeable from
 * the UI.
 */
@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface WorkspaceLocationMapper {

    LocationModel toModel(WorkspaceLocationEntity src);

    @Mapping(target = "workspaceUniqueId", ignore = true)
    WorkspaceLocationEntity toEntity(LocationModel src);
}
