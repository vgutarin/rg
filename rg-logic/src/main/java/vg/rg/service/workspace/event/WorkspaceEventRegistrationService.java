package vg.rg.service.workspace.event;

import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.unique.id.model.UniqueId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Registering a workspace's participants against its events, and ordering each event's roster.
 *
 * <p>Every method takes the event, the registration, or the workspace, so no operation can be expressed
 * without a scope. {@link #register} and {@link #list} are addressed by the event; {@link #unregister},
 * {@link #moveUp} and {@link #moveDown} by the registration's own identifier; {@link #countByEvents} by
 * the workspace.
 *
 * <p><strong>Capacity is not enforced.</strong> An event may be over-booked past its
 * {@code maxParticipantCount} deliberately — the count is shown, not imposed.
 */
public interface WorkspaceEventRegistrationService {

    /**
     * Registers a participant against an event, appended last in the roster.
     *
     * @throws IllegalArgumentException if the participant is already registered or does not belong to
     *                                  the event's workspace. Carries a stable message key.
     */
    WorkspaceEventRegistrationModel register(UniqueId eventId, UniqueId participantId);

    /** One event's roster, ordered, each entry carrying the participant's label. */
    List<WorkspaceEventRegistrationModel> list(UniqueId eventId);

    void unregister(UniqueId registrationId);

    /** Moves a registration one place earlier in its event's roster; a no-op at the top. */
    void moveUp(UniqueId registrationId);

    /** Moves a registration one place later in its event's roster; a no-op at the bottom. */
    void moveDown(UniqueId registrationId);

    /**
     * Registered counts for a set of the workspace's events, keyed by event identifier. Events with no
     * registrations are absent from the map; a caller reads an absent event as zero.
     */
    Map<UniqueId, Long> countByEvents(UniqueId workspaceId, Collection<UniqueId> eventIds);
}
