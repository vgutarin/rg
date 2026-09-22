package vg.rg.service.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.model.resource.ResourcePublication;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilityRule;
import vg.rg.model.workspace.WorkspaceEventAudienceModel;
import vg.unique.id.model.UniqueId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Lifecycle operations for publication state shared by resource types. */
public interface ResourcePublicationService {

    ResourcePublication create(UniqueId resourceId, boolean published, ResourceVisibilityMode mode,
                               List<ResourceVisibilityRule> rules);

    Optional<ResourcePublication> findByResourceUniqueId(UniqueId resourceId);

    /** Returns audience-safe workspace-event projections visible to the current principal. */
    Page<WorkspaceEventAudienceModel> browseWorkspaceEvents(Pageable pageable);

    /** Checks whether the current principal is admitted by a resource's audience publication policy. */
    boolean isResourceVisibleForCurrentUser(UniqueId resourceId);

    /** Returns an existing publication or creates the safe default for a legacy resource. */
    ResourcePublication ensureDraftPublication(UniqueId resourceId);

    ResourcePublication updatePublicationState(UniqueId resourceId, int version, boolean published);

    ResourcePublication replaceVisibilityPolicy(UniqueId resourceId, int version, ResourceVisibilityMode mode,
                                                List<ResourceVisibilityRule> rules);

    void delete(UniqueId resourceId);

    void deleteAll(Collection<UniqueId> resourceIds);
}
