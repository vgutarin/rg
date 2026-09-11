package vg.rg.service.workspace.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.unique.id.model.UniqueId;

/** Operations over events contained in a workspace. */
public interface WorkspaceEventService {

    WorkspaceEventModel create(UniqueId workspaceId, WorkspaceEventModel model);

    Page<WorkspaceEventModel> browse(UniqueId workspaceId, String titleFilter, Pageable pageable);

    WorkspaceEventModel update(WorkspaceEventModel model);

    void delete(UniqueId eventId);
}
