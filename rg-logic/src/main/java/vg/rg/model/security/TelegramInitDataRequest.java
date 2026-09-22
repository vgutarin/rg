package vg.rg.model.security;

public record TelegramInitDataRequest(String initData, boolean consentToKeepPersonalData) {

    /** Backwards-compatible normal sign-in request, which never creates a new identity. */
    public TelegramInitDataRequest(String initData) {
        this(initData, false);
    }

    public TelegramInitDataRequest {
        ContractValidation.required(initData, "initData");
        if (initData.isBlank()) {
            throw new IllegalArgumentException("initData is outside accepted bounds");
        }
    }
}
