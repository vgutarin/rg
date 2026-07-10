package vg.rg.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class SecureAuthorizationLimitsPropertiesTest {

    private static final String PROPERTY = "rg.secure-service.max-init-data-size";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Configuration.class);

    @Test
    void applicationContext_missingLimit_uses32KiBDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SecureAuthorizationLimitsProperties.class).maxInitDataBytes())
                    .isEqualTo(32L * 1024L);
        });
    }

    @Test
    void applicationContext_customPositiveLimit_usesConfiguredValue() {
        runner.withPropertyValues(PROPERTY + "=7KB")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecureAuthorizationLimitsProperties.class)
                            .maxInitDataBytes()).isEqualTo(7L * 1024L);
                });
    }

    @Test
    void applicationContext_zeroLimit_failsWithoutEchoingValue() {
        assertInvalidConfiguration("0B");
    }

    @Test
    void applicationContext_negativeLimit_failsWithoutEchoingValue() {
        assertInvalidConfiguration("-7KB");
    }

    @Test
    void applicationContext_malformedLimit_failsWithoutEchoingValue() {
        assertInvalidConfiguration("private-malformed-marker");
    }

    private void assertInvalidConfiguration(String configuredValue) {
        runner.withPropertyValues(PROPERTY + "=" + configuredValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessage("Invalid configuration for " + PROPERTY)
                            .hasMessageNotContaining(configuredValue);
                });
    }

    private static Throwable rootCause(Throwable failure) {
        var result = failure;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    @Import(SecureAuthorizationLimitsProperties.class)
    static class Configuration {
    }
}
