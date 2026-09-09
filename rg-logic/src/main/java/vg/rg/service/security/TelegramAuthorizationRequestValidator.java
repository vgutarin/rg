package vg.rg.service.security;

import org.springframework.stereotype.Component;
import vg.rg.config.security.SecureAuthorizationLimitsProperties;
import vg.rg.model.security.TelegramInitDataRequest;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public final class TelegramAuthorizationRequestValidator {

    private final SecureAuthorizationLimitsProperties limits;

    public TelegramAuthorizationRequestValidator(SecureAuthorizationLimitsProperties limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    public boolean isValid(TelegramInitDataRequest request) {
        if (request == null || request.initData() == null || request.initData().isBlank()) {
            return false;
        }
        return request.initData().getBytes(StandardCharsets.UTF_8).length
                <= limits.maxInitDataBytes();
    }
}
