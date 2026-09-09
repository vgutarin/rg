package vg.rg.model.security;

public record TelegramInitDataRequest(String initData) {

    public TelegramInitDataRequest {
        ContractValidation.required(initData, "initData");
        if (initData.isBlank()) {
            throw new IllegalArgumentException("initData is outside accepted bounds");
        }
    }
}
