package vg.rg.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vg.rg.entity.LocationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface LocationRepository extends UniqueIdJpaRepository<LocationEntity> {

    /**
     * Bounding-box prefilter over the indexed lat/lng columns. Callers refine the candidates with an
     * exact great-circle distance — this keeps the query portable across MySQL and H2.
     */
    @Query("""
            select l from LocationEntity l
            where l.latitude between :minLat and :maxLat
              and l.longitude between :minLng and :maxLng
            """)
    List<LocationEntity> findWithinBoundingBox(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng);

    Page<LocationEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
