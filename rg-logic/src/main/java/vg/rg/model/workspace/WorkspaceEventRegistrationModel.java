package vg.rg.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * One participant's registration to one event, as the event roster shows it.
 *
 * <p>A join between an event and a {@link WorkspaceParticipantModel}, ordered by {@link #orderBy} within
 * the event. {@link #participantLabel} is denormalised for display — resolved from the participant at
 * read time, never stored on the registration — so the roster can be rendered without a second lookup;
 * it carries no phone number, exactly as the participant roster model does not.
 *
 * <p>{@code eventUniqueId} and {@code participantUniqueId} reference their subjects by the identifier
 * minted for each (never {@code userUniqueId}), keeping a later identity binding free of rewrites.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceEventRegistrationModel implements Identifiable {

    private UniqueId uniqueId;
    private UniqueId eventUniqueId;
    private UniqueId participantUniqueId;
    private String participantLabel;
    private int orderBy;
    private UniqueId author;
    private UniqueId lastEditor;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;
}
