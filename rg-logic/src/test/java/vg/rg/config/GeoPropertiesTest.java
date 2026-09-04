package vg.rg.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoPropertiesTest {

    private static final String MATCH_RADIUS_METERS = "rg.geo.match-radius-meters";
    private static final String MAX_NAME_SEARCH_RESULTS = "rg.geo.max-name-search-results";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Configuration.class);

    @Test
    void defaults_areAppliedWhenNothingConfigured() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(GeoProperties.class);
            assertThat(properties.matchRadiusMeters()).isEqualTo(500);
            assertThat(properties.maxNameSearchResults()).isEqualTo(50);
        });
    }

    @Test
    void configuredValues_overrideDefaults() {
        runner.withPropertyValues(
                        MATCH_RADIUS_METERS + "=250",
                        MAX_NAME_SEARCH_RESULTS + "=10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(GeoProperties.class);
                    assertThat(properties.matchRadiusMeters()).isEqualTo(250);
                    assertThat(properties.maxNameSearchResults()).isEqualTo(10);
                });
    }

    /**
     * Relaxed binding is what lets a deployment set {@code RG_GEO_MATCH_RADIUS_METERS} directly, with no
     * {@code ${…}} placeholder in {@code application.properties} to relay it. The env-var spelling is
     * only recognised in a {@link SystemEnvironmentPropertySource}, so the source has to be a real one
     * here — {@code withPropertyValues} would register a plain map and silently not match.
     */
    @Test
    void relaxedBinding_acceptsEnvironmentVariableForm() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("RG_GEO_MATCH_RADIUS_METERS", "750"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GeoProperties.class).matchRadiusMeters()).isEqualTo(750);
                });
    }

    @Test
    void blankValue_fallsBackToDefault() {
        runner.withPropertyValues(MATCH_RADIUS_METERS + "=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GeoProperties.class).matchRadiusMeters()).isEqualTo(500);
                });
    }

    @Test
    void nonPositiveBound_isRejected() {
        assertInvalidConfiguration(MATCH_RADIUS_METERS, "0");
    }

    @Test
    void nonNumericBound_isRejected() {
        assertInvalidConfiguration(MAX_NAME_SEARCH_RESULTS, "many");
    }

    @Test
    void directConstruction_rejectsNonPositiveBound() {
        assertThatThrownBy(() -> new GeoProperties("500", "-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid configuration for " + MAX_NAME_SEARCH_RESULTS);
    }

    private void assertInvalidConfiguration(String property, String configuredValue) {
        runner.withPropertyValues(property + "=" + configuredValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(property);
                });
    }

    @EnableConfigurationProperties(GeoProperties.class)
    static class Configuration {
    }
}
