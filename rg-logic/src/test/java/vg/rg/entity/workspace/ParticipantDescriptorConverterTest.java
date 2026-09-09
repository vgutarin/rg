package vg.rg.entity.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.rg.config.EncryptionProperties;
import vg.rg.config.WorkspaceProperties;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.service.EncryptionService;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantDescriptorConverterTest {

    // Base64-encoded 32-byte AES-256 keys, matching EncryptionServiceTest's readable-ASCII convention.
    private static final String KEY_1 = "dGVzdC1zZWNyZXQta2V5LTEyMzQ1Njc4OTAxMjM0NTY=";
    private static final String KEY_2 = "bmV3LWN1cnJlbnQta2V5LWFiY2RlZmdoaWprbG1ub3A=";

    private EncryptionService encryptionService;
    private ParticipantDescriptorConverter converter;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(new EncryptionProperties(1, Map.of(1, KEY_1)));
        converter = new ParticipantDescriptorConverter(encryptionService, properties("2048B"));
    }

    @Test
    void nullDescriptor_staysNullInBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void descriptor_roundTrips() {
        var descriptor = ParticipantDescriptor.of("Ivan the plumber", "+380501112233");

        var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(descriptor));

        assertThat(restored).isEqualTo(descriptor);
    }

    /**
     * The whole point of the column: what lands in the database must not contain the person. Asserted on
     * the raw bytes rather than on a round trip, because a round trip would pass even if the converter
     * stored plaintext.
     */
    @Test
    void storedBytes_containNeitherLabelNorPhone() {
        var stored = converter.convertToDatabaseColumn(
                ParticipantDescriptor.of("Ivan Petrenko", "+380501112233"));

        assertThat(new String(stored, StandardCharsets.UTF_8))
                .doesNotContain("Ivan")
                .doesNotContain("Petrenko")
                .doesNotContain("380501112233");
        // The key id is stamped as the first byte so the value survives a rotation.
        assertThat(stored[0]).isEqualTo((byte) 1);
    }

    /** The IV is fresh per write, which is why no index or unique constraint could ever work here. */
    @Test
    void sameDescriptor_encryptsDifferentlyEachTime() {
        var descriptor = ParticipantDescriptor.of("Repeat", "+380501112233");

        assertThat(converter.convertToDatabaseColumn(descriptor))
                .isNotEqualTo(converter.convertToDatabaseColumn(descriptor));
    }

    @Test
    void absentPhone_isOmittedFromTheJsonRatherThanWrittenAsNull() {
        var stored = converter.convertToDatabaseColumn(ParticipantDescriptor.of("Label only", null));

        // Decoded through the service, so this inspects the JSON the converter actually produced.
        assertThat(encryptionService.decode(stored))
                .doesNotContain("\"p\"")
                .contains("\"v\":1")
                .contains("\"l\":\"Label only\"");
        assertThat(converter.convertToEntityAttribute(stored).phone()).isNull();
    }

    /**
     * A rollback to an older build must still read rows a newer one wrote, so an unrecognized key
     * cannot be an error. Simulated by sealing JSON that this version's record does not declare.
     */
    @Test
    void unknownJsonField_isIgnoredRatherThanRejected() {
        var forwardCompatible = encryptionService.encode(
                "{\"v\":1,\"l\":\"Ivan\",\"p\":\"+380501112233\",\"email\":\"someone@example.com\"}");

        var restored = converter.convertToEntityAttribute(forwardCompatible);

        assertThat(restored.label()).isEqualTo("Ivan");
        assertThat(restored.phone()).isEqualTo("+380501112233");
    }

    @Test
    void tamperedCiphertext_failsToOpen() {
        var stored = converter.convertToDatabaseColumn(
                ParticipantDescriptor.of("Ivan", "+380501112233"));
        stored[stored.length - 1] ^= 0x01;

        assertThatThrownBy(() -> converter.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Rotation is read-compatibility, not re-keying: a row written under key 1 must stay readable once
     * key 2 becomes current, because nothing rewrites existing rows.
     */
    @Test
    void rowWrittenUnderThePreviousKey_opensAfterRotation() {
        var stored = converter.convertToDatabaseColumn(ParticipantDescriptor.of("Ivan", null));

        var afterRotation = new ParticipantDescriptorConverter(
                new EncryptionService(new EncryptionProperties(2, Map.of(1, KEY_1, 2, KEY_2))),
                properties("2048B"));

        assertThat(afterRotation.convertToEntityAttribute(stored).label()).isEqualTo("Ivan");
    }

    /**
     * The size bound is enforced here as well as in the service. A bound applied only at the service
     * edge would not be a bound at all: anything reaching the repository directly would bypass it.
     */
    @Test
    void oversizedDescriptor_isRefusedByTheConverterItself() {
        var tiny = new ParticipantDescriptorConverter(encryptionService, properties("32B"));

        assertThatThrownBy(() -> tiny.convertToDatabaseColumn(
                ParticipantDescriptor.of("A label comfortably longer than thirty-two bytes", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    private static WorkspaceProperties properties(String descriptorMaxBytes) {
        return WorkspaceProperties.builder()
                .participantDescriptorMaxBytes(descriptorMaxBytes)
                .build();
    }
}
