package vg.rg.exception.workspace;

/**
 * This workspace already has a participant with that number.
 *
 * <p>Refusing is safe here, and worth being explicit about why: the comparison is against
 * <strong>this workspace's own roster</strong>, which the owner can already read in full. It discloses
 * nothing they did not already have. That is a different question from whether the number belongs to
 * someone with a platform account, which registration never reveals — see
 * {@code WorkspaceParticipantService}.
 */
public class DuplicateParticipantPhoneException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable outcome code; the UI owns its translation. */
    public static final String MESSAGE_KEY = "workspace.participant.error.duplicate-phone";

    public DuplicateParticipantPhoneException() {
        super(MESSAGE_KEY);
    }
}
