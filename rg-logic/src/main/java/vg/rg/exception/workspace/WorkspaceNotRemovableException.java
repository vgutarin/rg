package vg.rg.exception.workspace;

/**
 * The workspace cannot be removed. Raised for the owner's default workspace, which exists so that a
 * permission holder is never left without one — removing it would break that guarantee.
 */
public class WorkspaceNotRemovableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Stable outcome code; the UI owns its translation. */
    public static final String MESSAGE_KEY = "workspace.error.default-not-removable";

    public WorkspaceNotRemovableException() {
        super(MESSAGE_KEY);
    }
}
