package vg.rg.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * A participant as the roster shows them.
 *
 * <p><strong>This type deliberately cannot carry a phone number.</strong> Listing participants and
 * disclosing one's contact number are separate operations with separate permissions, and that separation
 * is only real if the read model has nowhere to put the plaintext. What it carries instead is
 * {@link #phoneRecorded} — whether there is a number at all, which is the whole of what the roster
 * shows. The number itself arrives only from {@code WorkspaceParticipantService.revealContact}.
 *
 * <p>{@code author}/{@code lastEditor} are abstract user identities recorded for auditing only.
 * {@code userUniqueId} is the participant's own identity subject once they have redeemed an invite, and
 * is null for everyone who has not — which is most of them, and permanently so for some.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceParticipantModel implements Identifiable {

    private UniqueId uniqueId;
    private UniqueId userUniqueId;
    private String label;
    private boolean phoneRecorded;
    private UniqueId author;
    private UniqueId lastEditor;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;
}
