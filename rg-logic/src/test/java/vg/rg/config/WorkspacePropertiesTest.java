package vg.rg.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePropertiesTest {

    private static final String MAX_PER_USER = "rg.workspace.max-per-user";
    private static final String NAME_MAX_LENGTH = "rg.workspace.name-max-length";
    private static final String DESCRIPTION_MAX_LENGTH = "rg.workspace.description-max-length";
    private static final String PARTICIPANTS_MAX = "rg.workspace.participants-max-per-workspace";
    private static final String DESCRIPTOR_MAX_BYTES = "rg.workspace.participant-descriptor-max-bytes";
    private static final String PARTICIPANT_LABEL_MAX_LENGTH = "rg.workspace.participant-label-max-length";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Configuration.class);

    @Test
    void defaults_areAppliedWhenNothingConfigured() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(WorkspaceProperties.class);
            assertThat(properties.maxPerUser()).isEqualTo(20);
            assertThat(properties.nameMaxLength()).isEqualTo(128);
            assertThat(properties.descriptionMaxLength()).isEqualTo(1024);
            assertThat(properties.participantsMaxPerWorkspace()).isEqualTo(1024);
            assertThat(properties.participantDescriptorMaxBytes()).isEqualTo(2048L);
            assertThat(properties.participantLabelMaxLength()).isEqualTo(128);
        });
    }

    @Test
    void configuredValues_overrideDefaults() {
        runner.withPropertyValues(
                        MAX_PER_USER + "=5",
                        NAME_MAX_LENGTH + "=64",
                        DESCRIPTION_MAX_LENGTH + "=256",
                        PARTICIPANTS_MAX + "=7",
                        DESCRIPTOR_MAX_BYTES + "=4KB",
                        PARTICIPANT_LABEL_MAX_LENGTH + "=32")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(WorkspaceProperties.class);
                    assertThat(properties.maxPerUser()).isEqualTo(5);
                    assertThat(properties.nameMaxLength()).isEqualTo(64);
                    assertThat(properties.descriptionMaxLength()).isEqualTo(256);
                    assertThat(properties.participantsMaxPerWorkspace()).isEqualTo(7);
                    assertThat(properties.participantDescriptorMaxBytes()).isEqualTo(4096L);
                    assertThat(properties.participantLabelMaxLength()).isEqualTo(32);
                });
    }

    /**
     * Production always supplies these keys — {@code application.properties} wraps each in a
     * {@code ${RG_WORKSPACE_…:default}} placeholder — so the value reaching the binder is blank, not
     * absent, whenever the environment variable is exported empty. That must still yield the default
     * rather than a startup failure or a silent zero.
     */
    @Test
    void blankValue_fallsBackToDefault() {
        runner.withPropertyValues(MAX_PER_USER + "=   ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WorkspaceProperties.class).maxPerUser()).isEqualTo(20);
                });
    }

    @Test
    void nonPositiveBound_isRejected() {
        assertInvalidConfiguration(MAX_PER_USER, "0");
    }

    @Test
    void nonNumericBound_isRejected() {
        assertInvalidConfiguration(NAME_MAX_LENGTH, "long");
    }

    @Test
    void nonPositiveParticipantBound_isRejected() {
        assertInvalidConfiguration(PARTICIPANTS_MAX, "0");
    }

    /**
     * The descriptor bound is a {@code DataSize}, not a plain integer, so it has its own parse path. A
     * value that is not a data size must be refused by naming the key alone — the same
     * no-disclosure rule as everywhere else in {@code ConfigurationBounds}.
     */
    @Test
    void nonSizeDescriptorBound_isRejected() {
        assertInvalidConfiguration(DESCRIPTOR_MAX_BYTES, "big");
    }

    @Test
    void directConstruction_rejectsNonPositiveBound() {
        assertThatThrownBy(() -> WorkspaceProperties.builder()
                .maxPerUser("20")
                .nameMaxLength("0")
                .descriptionMaxLength("1024")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid configuration for " + NAME_MAX_LENGTH);
    }

    private void assertInvalidConfiguration(String property, String configuredValue) {
        runner.withPropertyValues(property + "=" + configuredValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(property);
                });
    }

    @EnableConfigurationProperties(WorkspaceProperties.class)
    static class Configuration {
    }
}
