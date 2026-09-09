package vg.rg.service.security;

import org.junit.jupiter.api.Test;
import vg.rg.config.security.SecureAuthorizationLimitsProperties;
import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.model.security.TelegramInitDataRequest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramAuthorizationRequestValidatorTest {

    @Test
    void redeem_defaultLimit_acceptsExactly32KiBBeforeFacadeInvocation() {
        var calls = new AtomicInteger();
        var service = service(null, calls);

        var outcome = service.redeem(new TelegramInitDataRequest("a".repeat(32 * 1024)));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.DENIED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void redeem_defaultLimit_rejectsOneByteOverBeforeFacadeInvocation() {
        var calls = new AtomicInteger();
        var service = service(null, calls);

        var outcome = service.redeem(new TelegramInitDataRequest("a".repeat(32 * 1024 + 1)));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.INVALID_REQUEST);
        assertThat(calls).hasValue(0);
    }

    @Test
    void redeem_customLimit_countsUtf8BytesAtBoundary() {
        var calls = new AtomicInteger();
        var service = service("4B", calls);

        var outcome = service.redeem(new TelegramInitDataRequest("éé"));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.DENIED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void redeem_customLimit_rejectsOneUtf8ByteOverBeforeFacadeInvocation() {
        var calls = new AtomicInteger();
        var service = service("4B", calls);

        var outcome = service.redeem(new TelegramInitDataRequest("ééa"));

        assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.INVALID_REQUEST);
        assertThat(calls).hasValue(0);
    }

    private static AuthorizationApplicationService service(String limit, AtomicInteger calls) {
        var properties = new SecureAuthorizationLimitsProperties(limit);
        var validator = new TelegramAuthorizationRequestValidator(properties);
        SecureAuthorizationFacade facade = request -> {
            calls.incrementAndGet();
            return AuthorizationOutcome.denied();
        };
        return new AuthorizationApplicationService(facade, validator);
    }
}
