package vg.rg.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePropertiesTest {

    @Test
    void defaults_areAppliedWhenNothingConfigured() {
        var properties = new WorkspaceProperties(new MockEnvironment());

        assertThat(properties.maxPerUser()).isEqualTo(20);
        assertThat(properties.nameMaxLength()).isEqualTo(128);
        assertThat(properties.descriptionMaxLength()).isEqualTo(1024);
    }

    @Test
    void configuredValues_overrideDefaults() {
        var environment = new MockEnvironment()
                .withProperty(WorkspaceProperties.MAX_PER_USER_PROPERTY, "5")
                .withProperty(WorkspaceProperties.NAME_MAX_LENGTH_PROPERTY, "64")
                .withProperty(WorkspaceProperties.DESCRIPTION_MAX_LENGTH_PROPERTY, "256");

        var properties = new WorkspaceProperties(environment);

        assertThat(properties.maxPerUser()).isEqualTo(5);
        assertThat(properties.nameMaxLength()).isEqualTo(64);
        assertThat(properties.descriptionMaxLength()).isEqualTo(256);
    }

    @Test
    void blankValue_fallsBackToDefault() {
        var environment = new MockEnvironment()
                .withProperty(WorkspaceProperties.MAX_PER_USER_PROPERTY, "   ");

        assertThat(new WorkspaceProperties(environment).maxPerUser()).isEqualTo(20);
    }

    @Test
    void nonPositiveBound_isRejected() {
        var environment = new MockEnvironment()
                .withProperty(WorkspaceProperties.MAX_PER_USER_PROPERTY, "0");

        assertThatThrownBy(() -> new WorkspaceProperties(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(WorkspaceProperties.MAX_PER_USER_PROPERTY);
    }

    @Test
    void nonNumericBound_isRejected() {
        var environment = new MockEnvironment()
                .withProperty(WorkspaceProperties.NAME_MAX_LENGTH_PROPERTY, "long");

        assertThatThrownBy(() -> new WorkspaceProperties(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(WorkspaceProperties.NAME_MAX_LENGTH_PROPERTY);
    }

}
