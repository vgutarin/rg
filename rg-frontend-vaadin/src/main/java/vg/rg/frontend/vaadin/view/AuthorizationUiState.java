package vg.rg.frontend.vaadin.view;

public enum AuthorizationUiState {
    LOADING("auth.loading"),
    PERMITTED("auth.permitted"),
    NO_ACCESS("auth.no-access"),
    DENIED("auth.denied"),
    TEMPORARILY_UNAVAILABLE("auth.unavailable"),
    INCOMPATIBLE("auth.incompatible"),
    RETRYING("auth.retrying");

    private final String messagePrefix;

    AuthorizationUiState(String messagePrefix) {
        this.messagePrefix = messagePrefix;
    }

    String messagePrefix() {
        return messagePrefix;
    }

    boolean showsProgress() {
        return this == LOADING || this == RETRYING;
    }

    boolean showsRetry() {
        return this == TEMPORARILY_UNAVAILABLE || this == RETRYING;
    }

    boolean focusesHeading() {
        return this == NO_ACCESS
                || this == DENIED
                || this == TEMPORARILY_UNAVAILABLE
                || this == INCOMPATIBLE;
    }
}
