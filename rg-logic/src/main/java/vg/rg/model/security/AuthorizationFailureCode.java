package vg.rg.model.security;

public enum AuthorizationFailureCode {
    AUTHORIZATION_DENIED,
    INVALID_REQUEST,
    AUTHORIZATION_EXPIRED,
    SERVICE_UNAVAILABLE,
    INCOMPATIBLE_VERSION,
    RATE_LIMITED
}
