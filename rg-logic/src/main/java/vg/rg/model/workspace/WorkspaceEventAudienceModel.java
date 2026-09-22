package vg.rg.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.rg.model.geo.LocationAudienceModel;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** Sanitised event projection for a viewer outside its owning workspace. */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceEventAudienceModel implements Identifiable {
    private UniqueId uniqueId;
    private String title;
    private Instant startAt;
    private Instant endAt;
    private WorkspaceEventType eventType;
    private Integer maxParticipantCount;
    private boolean isPublished;
    private LocationAudienceModel location;
    private boolean membershipPending;
    private boolean membershipBound;
}
