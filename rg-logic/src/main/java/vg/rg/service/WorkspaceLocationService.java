package vg.rg.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.unique.id.model.UniqueId;

import java.util.List;

/**
 * Business operations over locations inside a workspace. Every method takes either the workspace or a
 * resource identifier, so no operation can be expressed without a scope — there is no unscoped overload
 * to reach for.
 *
 * <p>Guards pass the identifier of the thing being acted upon: the location's own identifier for
 * update/delete, the workspace for create and for reads. Behaviour otherwise matches the retired global
 * service — coordinates optional, proximity advisory and nearest-first, bounded name search — with every
 * query restricted to the one workspace.
 *
 * <p>See {@code specs/003-workspace-layer/contracts/workspace-location-service.md}.
 */
public interface WorkspaceLocationService {

    LocationModel create(UniqueId workspaceId, LocationModel model);

    LocationModel update(LocationModel model);

    void delete(UniqueId locationId);

    Page<LocationModel> browse(UniqueId workspaceId, Pageable pageable);

    List<LocationModel> searchByName(UniqueId workspaceId, String query, int limit);

    /**
     * Advisory proximity suggestion within the configured radius, nearest-first, scoped to the given
     * workspace. Never blocks creation, and never looks outside that workspace.
     */
    List<ProximityMatch> findNearby(UniqueId workspaceId, ProximityQuery query);
}
