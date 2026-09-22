package vg.rg.service.workspace.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.resource.ResourceVisibilityPolicy;
import vg.unique.id.model.UniqueId;

/** Operations over events contained in a workspace. */
public interface WorkspaceEventService {

    WorkspaceEventModel create(UniqueId workspaceId, WorkspaceEventModel model);

    Page<WorkspaceEventModel> browse(UniqueId workspaceId, String titleFilter, Pageable pageable);

    WorkspaceEventModel update(WorkspaceEventModel model);

    WorkspaceEventModel replaceVisibilityPolicy(
            UniqueId eventId, int version, ResourceVisibilityPolicy policy);

    void delete(UniqueId eventId);
}
