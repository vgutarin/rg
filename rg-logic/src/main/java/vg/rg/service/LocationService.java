package vg.rg.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.unique.id.model.UniqueId;

import java.util.List;

/**
 * Business operations over the shared location collection. Each method is authorized against the
 * caller's granted permissions; author/last-editor audit fields are populated by JPA auditing.
 * See {@code specs/002-geolocation-module/contracts/location-service.md}.
 */
public interface LocationService {

    LocationModel create(LocationModel model);

    LocationModel update(LocationModel model);

    void delete(UniqueId id);

    /**
     * Advisory proximity suggestion: saved locations within the configured radius (default ±500 m),
     * nearest-first. Never blocks creation and {@link #create} never consults it.
     */
    List<ProximityMatch> findNearby(ProximityQuery query);

    List<LocationModel> searchByName(String query, int limit);

    Page<LocationModel> browse(Pageable pageable);
}
