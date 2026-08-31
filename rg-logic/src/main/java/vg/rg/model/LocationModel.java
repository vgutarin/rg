package vg.rg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A saved location in the shared collection. {@code author}/{@code lastEditor} are abstract user
 * identities recorded for auditing only.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class LocationModel implements Identifiable {

    private UniqueId uniqueId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String name;
    private String description;
    private String googlePlaceId;
    private UniqueId author;
    private UniqueId lastEditor;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;
}
