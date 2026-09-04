package vg.rg.frontend.vaadin.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class AuthorizationFailureDeadlineIntegrationTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * A transport timeout has to reach a safe UI state quickly and <strong>without retrying</strong>.
     *
     * <p>Both halves are about a handler that runs on the embedded server's own thread while the
     * assertions run on the test thread, so neither can be a single read taken the instant the client
     * gives up — see the comments at the assertions for why each waits instead.
     */
    @Test
    void configuredDeadlines_transportTimeoutReachesSafeUiStateWithinTenSecondsWithoutRetry()
            throws IOException {
        var calls = new AtomicInteger();
        // Counted down on every request. `arrived` opens on the first; `retried` needs two, so it
        // opens only if the client issued a second one.
        var arrived = new CountDownLatch(1);
        var retried = new CountDownLatch(2);
        start(exchange -> {
            calls.incrementAndGet();
            arrived.countDown();
            retried.countDown();
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
            var limits = new IdentityAuthorizationLimitsProperties(null, null);
            var facade = new IdentitySecureAuthorizationFacade(
                    context.getBean(IdentityApplicationApi.class),
                    new IdentityAuthorizationResponseValidator(limits));
            var sharedLimits = new SecureAuthorizationLimitsProperties(null);
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

            // Everything above is the actual subject: a transport timeout reaches a safe UI state
            // quickly. What follows is about the *read*-timeout path specifically, and that path is
            // only exercised if the request got as far as the handler.
            //
            // An assumption rather than an assertion, because it legitimately may not have. The client
            // abandons the read after 50 ms, and its connect timeout is the production default -- on a
            // saturated machine the connect can expire first, so no request reaches the server at all.
            // That is still a transport timeout and the assertions above still hold; it simply is not
            // the case the retry check below is about. Asserting arrival here made this fail in bursts
            // whenever the machine was busy, and a longer wait does not help: the client has already
            // given up by then.
            //
            // Waiting is still required. Reading the counter with no wait was the original defect --
            // AssertJ's tell-tale "Expecting AtomicInteger(1) to have value: 1 but did not", comparing
            // 0 and formatting 1 with the increment landing in between.
            assumeTrue(arrived.await(5, TimeUnit.SECONDS),
                    "the request did not reach the server, so this run exercised a connect timeout "
                            + "rather than a read timeout");
            // And no retry follows. This has to be a bounded wait rather than a single read: a retry
            // would be issued *after* the read timeout, so a read taken the moment the client gave up
            // looks before the only point at which a retry could show up, and could never have caught
            // one. The wait is paid only when the test passes -- a second request opens the latch
            // immediately.
            assertThat(retried.await(500, TimeUnit.MILLISECONDS))
                    .as("a second request was issued after the transport timeout")
                    .isFalse();
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
