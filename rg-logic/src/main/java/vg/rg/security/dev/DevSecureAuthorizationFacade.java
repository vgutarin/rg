package vg.rg.security.dev;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import vg.rg.security.SecureAuthorizationFacade;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.security.model.TelegramInitDataRequest;

import java.time.Clock;

@Service
@ConditionalOnProperty(prefix = "rg.secure-service", name = "enabled", havingValue = "true")
public class DevSecureAuthorizationFacade implements SecureAuthorizationFacade {

    private final TelegramInitDataVerifier verifier;

    @Autowired
    public DevSecureAuthorizationFacade(DevSecureServiceProperties properties,
                                        Environment environment,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this(
                new TelegramInitDataVerifier(
                        objectMapper,
                        clock,
                        botToken(properties, environment),
                        properties.getAuthDateTtl(),
                        properties.getAllowedClockSkew()));
    }

    public DevSecureAuthorizationFacade(TelegramInitDataVerifier verifier) {
        this.verifier = java.util.Objects.requireNonNull(verifier);
    }

    @Override
    public AuthorizationOutcome redeemAuthorizationGrant(TelegramInitDataRequest request) {
        var verification = verifier.verify(request);
        if (verification.status() == TelegramInitDataVerifier.Status.EXPIRED) {
            return AuthorizationOutcome.expired();
        }
        if (verification.status() != TelegramInitDataVerifier.Status.VALID || verification.telegramUserId().isEmpty()) {
            return AuthorizationOutcome.invalidRequest();
        }
        var telegramUserId = verification.telegramUserId().getAsLong();
        return AuthorizationOutcome.authorized(new AuthenticatedUserPrincipal(
                Long.toString(telegramUserId),
                null,
                Permissions.ALL,
                true,
                AuthenticationFlow.TELEGRAM));
    }

    private static String botToken(DevSecureServiceProperties properties, Environment environment) {
        return properties.getBotToken().isBlank()
                ? environment.getProperty("telegram.bot.token", "")
                : properties.getBotToken();
    }
}
