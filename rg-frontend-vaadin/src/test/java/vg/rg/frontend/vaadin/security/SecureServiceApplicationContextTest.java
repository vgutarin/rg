package vg.rg.frontend.vaadin.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import vg.identity.rest.IdentityRestClientAutoConfig;
import vg.rg.security.AuthorizationApplicationService;
import vg.rg.security.SecureAuthorizationFacade;
import vg.rg.security.SecureAuthorizationLimitsProperties;
import vg.rg.security.TelegramAuthorizationRequestValidator;
import vg.rg.security.dev.DevSecureAuthorizationFacade;
import vg.rg.security.dev.DevSecureServiceProperties;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class SecureServiceApplicationContextTest {

    private static final String COUNT = "rg.secure-service.identity.max-permission-count";
    private static final String LENGTH = "rg.secure-service.identity.max-permission-length";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityRestClientAutoConfig.class))
            .withUserConfiguration(SecureComponents.class)
            .withPropertyValues(
                    "rg.secure-service.bot-token=synthetic-test-token",
                    "vg.identity.rest-client.base-url=http://127.0.0.1:9",
                    "vg.identity.rest-client.api-key=clearly-fake-test-key",
                    "vg.identity.rest-client.connect-timeout=PT2S",
                    "vg.identity.rest-client.read-timeout=PT8S");

    @Test
    void applicationContext_missingLimits_usesDefaults() {
        runner.withPropertyValues("rg.secure-service.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var limits = context.getBean(IdentityAuthorizationLimitsProperties.class);
                    assertThat(limits.maxPermissionCount()).isEqualTo(1024);
                    assertThat(limits.maxPermissionLength()).isEqualTo(128);
                });
    }

    @Test
    void applicationContext_customPositiveLimits_usesConfiguredValues() {
        runner.withPropertyValues(
                        "rg.secure-service.enabled=true",
                        COUNT + "=7",
                        LENGTH + "=19")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var limits = context.getBean(IdentityAuthorizationLimitsProperties.class);
                    assertThat(limits.maxPermissionCount()).isEqualTo(7);
                    assertThat(limits.maxPermissionLength()).isEqualTo(19);
                });
    }

    @Test
    void applicationContext_invalidLimits_failWithoutConfiguredValue() {
        assertInvalid(COUNT, "0");
        assertInvalid(LENGTH, "-1");
        assertInvalid(COUNT, "sensitive-malformed-marker");
    }

    @Test
    void applicationContext_fakeKeyDevelopmentMode_selectsOnlyDevelopmentFacade() {
        runner.withPropertyValues("rg.secure-service.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecureAuthorizationFacade.class);
                    assertThat(context.getBean(SecureAuthorizationFacade.class))
                            .isInstanceOf(DevSecureAuthorizationFacade.class);
                });
    }

    @Test
    void applicationContext_falseOrMissingSelection_selectsOnlyIdentityFacade() {
        runner.withPropertyValues("rg.secure-service.enabled=false")
                .run(SecureServiceApplicationContextTest::assertIdentityFacade);
        runner.run(SecureServiceApplicationContextTest::assertIdentityFacade);
    }

    private void assertInvalid(String property, String configuredValue) {
        runner.withPropertyValues(
                        "rg.secure-service.enabled=true",
                        property + "=" + configuredValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessage("Invalid configuration for " + property)
                            .hasMessageNotContaining(configuredValue);
                });
    }

    private static void assertIdentityFacade(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(SecureAuthorizationFacade.class);
        assertThat(context.getBean(SecureAuthorizationFacade.class))
                .isInstanceOf(IdentitySecureAuthorizationFacade.class);
    }

    private static Throwable rootCause(Throwable failure) {
        var result = failure;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DevSecureServiceProperties.class)
    @Import({
            DevSecureAuthorizationFacade.class,
            IdentitySecureAuthorizationFacade.class,
            SecureAuthorizationLimitsProperties.class,
            TelegramAuthorizationRequestValidator.class,
            IdentityAuthorizationLimitsProperties.class,
            IdentityAuthorizationResponseValidator.class,
            AuthorizationApplicationService.class
    })
    static class SecureComponents {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }
}
