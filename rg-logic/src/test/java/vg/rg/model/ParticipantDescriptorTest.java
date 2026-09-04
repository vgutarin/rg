package vg.rg.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantDescriptorTest {

    @Test
    void of_stampsTheCurrentSchemaVersion() {
        // Callers never choose the version: a payload written without one could not be reinterpreted
        // later, and the stored envelope carries no version byte to fall back on.
        assertThat(ParticipantDescriptor.of("Plumber", "+380501112233").schemaVersion())
                .isEqualTo(ParticipantDescriptor.CURRENT_SCHEMA_VERSION);
    }

    /**
     * The single most likely way this design leaks: a record's generated {@code toString()} prints every
     * component, so one log line, exception message, or debugger evaluation would disclose both fields.
     */
    @Test
    void toString_disclosesNeitherLabelNorPhone() {
        var descriptor = ParticipantDescriptor.of("Ivan Petrenko", "+380501112233");

        assertThat(descriptor.toString())
                .doesNotContain("Ivan")
                .doesNotContain("Petrenko")
                .doesNotContain("380")
                .doesNotContain("1112233")
                .contains("schemaVersion=" + ParticipantDescriptor.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void toString_withoutAPhone_stillDisclosesNothing() {
        assertThat(ParticipantDescriptor.of("Solo Label", null).toString())
                .doesNotContain("Solo")
                .doesNotContain("Label");
    }
}
