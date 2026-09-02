package vg.rg.service;

/**
 * The caller already owns the maximum number of workspaces. Carries a stable message key and the limit,
 * so the UI can state the bound without the business layer knowing any language.
 */
public class WorkspaceLimitReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable outcome code; the UI owns its translation. */
    public static final String MESSAGE_KEY = "workspace.error.limit-reached";

    private final int limit;

    public WorkspaceLimitReachedException(int limit) {
        super(MESSAGE_KEY);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
