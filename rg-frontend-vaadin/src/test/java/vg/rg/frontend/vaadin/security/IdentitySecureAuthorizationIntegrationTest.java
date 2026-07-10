package vg.rg.frontend.vaadin.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import vg.identity.rest.IdentityRestClientAutoConfig;
import vg.identity.rest.IdentityRestClientProperties;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySecureAuthorizationIntegrationTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loopback_success_mapsPublishedClientResponse() throws IOException {
        start(exchange -> respond(exchange, 200, """
                {"sub":"subject-1","name":null,"permissions":["home:view"],"consentGiven":true}
                """));

        clientRunner(Duration.ofSeconds(1), Duration.ofSeconds(1)).run(context -> {
            assertThat(context).hasNotFailed();
            var outcome = facade(context.getBean(IdentityApplicationApi.class))
                    .redeemAuthorizationGrant(request());

            assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.AUTHORIZED);
            assertThat(outcome.principal().orElseThrow().permissions()).containsExactly("home:view");
        });
    }

    @Test
    void loopback_readTimeout_mapsToUnavailableWithoutAutomaticRetry() throws IOException {
        var calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        clientRunner(Duration.ofSeconds(1), Duration.ofMillis(50)).run(context -> {
            var outcome = facade(context.getBean(IdentityApplicationApi.class))
                    .redeemAuthorizationGrant(request());

            assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.UNAVAILABLE);
            assertThat(calls).hasValue(1);
        });
    }

    @Test
    void loopback_serverFailure_mapsToUnavailableWithoutAutomaticRetry() throws IOException {
        var calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "synthetic upstream detail");
        });

        clientRunner(Duration.ofSeconds(1), Duration.ofSeconds(1)).run(context -> {
            var outcome = facade(context.getBean(IdentityApplicationApi.class))
                    .redeemAuthorizationGrant(request());

            assertThat(outcome.status()).isEqualTo(AuthorizationOutcome.Status.UNAVAILABLE);
            assertThat(outcome.toString()).doesNotContain("synthetic upstream detail");
            assertThat(calls).hasValue(1);
        });
    }

    @Test
    void publishedClient_capabilityAudit_exposesCurrentPhaseFourBlockers() {
        assertThat(IdentityRestClientProperties.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .contains("connectTimeout", "readTimeout")
                .doesNotContain("requestTimeout", "maxResponseSize");
        assertThat(vg.identity.model.IdentityApplicationUserPrincipal.class.getRecordComponents())
                .filteredOn(component -> component.getName().equals("permissions"))
                .singleElement()
                .satisfies(component -> assertThat(component.getType()).isEqualTo(Set.class));
    }

    private ApplicationContextRunner clientRunner(Duration connectTimeout, Duration readTimeout) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdentityRestClientAutoConfig.class))
                .withPropertyValues(
                        "vg.identity.rest-client.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                        "vg.identity.rest-client.api-key=clearly-fake-test-key",
                        "vg.identity.rest-client.connect-timeout=" + connectTimeout,
                        "vg.identity.rest-client.read-timeout=" + readTimeout);
    }

    private IdentitySecureAuthorizationFacade facade(IdentityApplicationApi api) {
        var limits = new IdentityAuthorizationLimitsProperties(new MockEnvironment());
        return new IdentitySecureAuthorizationFacade(
                api, new IdentityAuthorizationResponseValidator(limits));
    }

    private TelegramInitDataRequest request() {
        return new TelegramInitDataRequest("auth_date=1&hash=x");
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/me/authentications/telegram", handler);
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
