package vg.rg.security.model;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class AuthorizationOutcome {

    public enum Status { AUTHORIZED, DENIED, INVALID_REQUEST, EXPIRED, UNAVAILABLE, INCOMPATIBLE }

    private final Status status;
    private final AuthenticatedUserPrincipal principal;
    private final AuthorizationFailureCode failureCode;
    private final Duration retryAfter;

    private AuthorizationOutcome(Status status, AuthenticatedUserPrincipal principal,
                                 AuthorizationFailureCode failureCode, Duration retryAfter) {
        this.status = Objects.requireNonNull(status);
        this.principal = principal;
        this.failureCode = failureCode;
        this.retryAfter = retryAfter;
        if ((status == Status.AUTHORIZED) != (principal != null)) {
            throw new IllegalArgumentException("Only an authorized outcome may contain a principal");
        }
    }

    public static AuthorizationOutcome authorized(AuthenticatedUserPrincipal principal) {
        return new AuthorizationOutcome(Status.AUTHORIZED, Objects.requireNonNull(principal), null, null);
    }

    public static AuthorizationOutcome denied() {
        return failure(Status.DENIED, AuthorizationFailureCode.AUTHORIZATION_DENIED);
    }

    public static AuthorizationOutcome invalidRequest() {
        return failure(Status.INVALID_REQUEST, AuthorizationFailureCode.INVALID_REQUEST);
    }

    public static AuthorizationOutcome expired() {
        return failure(Status.EXPIRED, AuthorizationFailureCode.AUTHORIZATION_EXPIRED);
    }

    public static AuthorizationOutcome unavailable(Duration retryAfter) {
        if (retryAfter != null && (retryAfter.isNegative() || retryAfter.isZero() || retryAfter.compareTo(Duration.ofSeconds(60)) > 0)) {
            throw new IllegalArgumentException("retryAfter is outside accepted bounds");
        }
        return new AuthorizationOutcome(Status.UNAVAILABLE, null,
                AuthorizationFailureCode.SERVICE_UNAVAILABLE, retryAfter);
    }

    public static AuthorizationOutcome incompatible() {
        return failure(Status.INCOMPATIBLE, AuthorizationFailureCode.INCOMPATIBLE_VERSION);
    }

    private static AuthorizationOutcome failure(Status status, AuthorizationFailureCode code) {
        return new AuthorizationOutcome(status, null, code, null);
    }

    public Status status() { return status; }
    public Optional<AuthenticatedUserPrincipal> principal() { return Optional.ofNullable(principal); }
}
