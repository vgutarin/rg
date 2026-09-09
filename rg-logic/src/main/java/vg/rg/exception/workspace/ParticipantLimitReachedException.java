package vg.rg.exception.workspace;

/**
 * The workspace already holds the maximum number of participants. Mirrors
 * {@link WorkspaceLimitReachedException}: a stable message key plus the limit, so the UI can state the
 * bound without the business layer knowing any language.
 */
public class ParticipantLimitReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable outcome code; the UI owns its translation. */
    public static final String MESSAGE_KEY = "workspace.participant.error.limit-reached";

    private final int limit;

    public ParticipantLimitReachedException(int limit) {
        super(MESSAGE_KEY);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
