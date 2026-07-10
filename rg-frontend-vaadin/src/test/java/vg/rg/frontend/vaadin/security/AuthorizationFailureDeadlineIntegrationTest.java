package vg.rg.frontend.vaadin.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import vg.identity.rest.IdentityRestClientAutoConfig;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.telegram.TelegramAuthView;
import vg.rg.frontend.vaadin.view.AuthorizationUiState;
import vg.rg.security.AuthorizationApplicationService;
import vg.rg.security.SecureAuthorizationLimitsProperties;
import vg.rg.security.TelegramAuthorizationRequestValidator;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthorizationFailureDeadlineIntegrationTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void configuredDeadlines_transportTimeoutReachesSafeUiStateWithinTenSecondsWithoutRetry()
            throws IOException {
        var calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(200);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        var defaults = applicationDefaults();
        var connectTimeout = defaultDuration(defaults.getProperty(
                "vg.identity.rest-client.connect-timeout"));
        var readTimeout = defaultDuration(defaults.getProperty(
                "vg.identity.rest-client.read-timeout"));
        assertThat(connectTimeout.plus(readTimeout)).isLessThanOrEqualTo(Duration.ofSeconds(10));

        clientRunner(connectTimeout, Duration.ofMillis(50)).run(context -> {
            assertThat(context).hasNotFailed();
            var limits = new IdentityAuthorizationLimitsProperties(new MockEnvironment());
            var facade = new IdentitySecureAuthorizationFacade(
                    context.getBean(IdentityApplicationApi.class),
                    new IdentityAuthorizationResponseValidator(limits));
            var sharedLimits = new SecureAuthorizationLimitsProperties(new MockEnvironment());
            var service = new AuthorizationApplicationService(
                    facade, new TelegramAuthorizationRequestValidator(sharedLimits));
            var view = new TelegramAuthView(
                    service, mock(ApplicationSecurityContextService.class), localization());

            var started = System.nanoTime();
            view.authenticate("auth_date=1&hash=x");
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
            assertThat(view.authorizationState())
                    .isEqualTo(AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
            assertThat(calls).hasValue(1);
        });
    }

    private ApplicationContextRunner clientRunner(Duration connectTimeout, Duration readTimeout) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdentityRestClientAutoConfig.class))
                .withPropertyValues(
                        "vg.identity.rest-client.base-url=http://127.0.0.1:"
                                + server.getAddress().getPort(),
                        "vg.identity.rest-client.api-key=clearly-fake-test-key",
                        "vg.identity.rest-client.connect-timeout=" + connectTimeout,
                        "vg.identity.rest-client.read-timeout=" + readTimeout);
    }

    private Properties applicationDefaults() throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            properties.load(java.util.Objects.requireNonNull(stream));
        }
        return properties;
    }

    private Duration defaultDuration(String placeholder) {
        var separator = placeholder.lastIndexOf(':');
        return Duration.parse(placeholder.substring(separator + 1, placeholder.length() - 1));
    }

    private LocalizationService localization() {
        var messages = new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new LocalizationService(messages);
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
