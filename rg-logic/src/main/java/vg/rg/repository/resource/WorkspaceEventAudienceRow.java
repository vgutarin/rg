package vg.rg.repository.resource;

import vg.rg.model.workspace.WorkspaceEventType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Flat database projection for a workspace event disclosed to an audience viewer.
 *
 * <p>It contains no entities, identifiers for related content, or audit metadata, so the service can
 * construct its audience model without per-row lookups.
 */
public interface WorkspaceEventAudienceRow {

    Long getUniqueId();

    String getTitle();

    Instant getStartAt();

    Instant getEndAt();

    WorkspaceEventType getEventType();

    Integer getMaxParticipantCount();

    BigDecimal getLocationLatitude();

    BigDecimal getLocationLongitude();

    String getLocationName();

    String getLocationDescription();

    String getLocationGooglePlaceId();

    boolean isMembershipPending();

    boolean isMembershipBound();
}
