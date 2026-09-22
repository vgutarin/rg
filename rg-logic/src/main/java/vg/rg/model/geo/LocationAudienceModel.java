package vg.rg.model.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Location content safe to disclose with an audience-facing resource.
 *
 * <p>Identity, audit metadata, timestamps, and persistence version stay private to workspace-management
 * operations.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class LocationAudienceModel {

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String name;
    private String description;
    private String googlePlaceId;
}
