package vg.rg.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.rg.config.EncryptionProperties;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    // Base64-encoded 32-byte AES-256 keys.
    private static final String KEY = "dGVzdC1zZWNyZXQta2V5LTEyMzQ1Njc4OTAxMjM0NTY=";
    private static final String OTHER_KEY = "YW5vdGhlci1zZWNyZXQta2V5LTA5ODc2NTQzMjF6eXg=";
    private static final String NEW_KEY = "bmV3LWN1cnJlbnQta2V5LWFiY2RlZmdoaWprbG1ub3A=";
    // Base64 of 16 bytes -> wrong length for AES-256.
    private static final String SHORT_KEY = "c2l4dGVlbi1ieXRlLWtleQ==";

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(new EncryptionProperties(1, Map.of(1, KEY)));
    }

    @Test
    void encode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.encode(null)).isNull();
    }

    @Test
    void encode_whenInputIsEmpty_returnsEncodedValue() {
        var encoded = encryptionService.encode("");

        assertThat(encoded).isNotNull();
        assertThat(encryptionService.decode(encoded)).isEmpty();
    }

    @Test
    void decode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.decode(null)).isNull();
    }

    @Test
    void encodeAndDecode_whenInputIsValid_returnsOriginalString() {
        var plaintext = "Hello, World! " + UUID.randomUUID();
        var encoded = encryptionService.encode(plaintext);

        assertThat(encoded).isNotNull();
        assertThat(new String(encoded)).isNotEqualTo(plaintext);
        assertThat(encryptionService.decode(encoded)).isEqualTo(plaintext);
    }

    @Test
    void encode_stampsCurrentKeyIdAsFirstByte() {
        var encoded = encryptionService.encode("payload");

        assertThat(encoded[0]).isEqualTo((byte) 1);
    }

    @Test
    void encode_whenSameInputIsEncodedTwice_returnsDifferentResults() {
        var plaintext = "same-input";
        var encoded1 = encryptionService.encode(plaintext);
        var encoded2 = encryptionService.encode(plaintext);

        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(encryptionService.decode(encoded1)).isEqualTo(plaintext);
        assertThat(encryptionService.decode(encoded2)).isEqualTo(plaintext);
    }

    @Test
    void decode_whenDataIsInvalid_throwsException() {
        assertThatThrownBy(() -> encryptionService.decode(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    /**
     * Pins the on-disk format: {@code [keyId:1][iv:12][ciphertext+tag]}. The vector below was produced
     * independently from the format description with plain JCE, not by calling {@code encode}, so this
     * asserts the layout itself rather than that the class agrees with itself. A change that breaks it
     * makes every already-persisted value unreadable.
     */
    @Test
    void decode_whenGivenPinnedFormatVector_returnsExpectedPlaintext() {
        var vector = HexFormat.of().parseHex(
                "07101112131415161718191a1b9dafd1ecba286a65d5a4009f80afcb1daa9235b5e7b2ae7128e8edb39673a816be98ed");
        var service = new EncryptionService(new EncryptionProperties(7, Map.of(7, KEY)));

        assertThat(vector).hasSize(48);
        assertThat(service.decode(vector)).isEqualTo("rg-format-vector-v1");
    }

    @Test
    void constructor_whenPropertiesAreNull_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_whenKeysAreNull_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(1, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rg.encryption.keys");
    }

    @Test
    void constructor_whenKeysAreEmpty_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(1, Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rg.encryption.keys");
    }

    @Test
    void constructor_whenCurrentKeyIdIsMissing_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(null, Map.of(1, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-key-id");
    }

    @Test
    void constructor_whenCurrentKeyIdIsNotInKeyring_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(2, Map.of(1, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-key-id");
    }

    @Test
    void constructor_whenKeyIdIsOutOfRange_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(256, Map.of(256, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("range");
    }

    @Test
    void constructor_whenKeyIsNotValidBase64_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(1, Map.of(1, "not valid base64 !!!"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void constructor_whenKeyHasWrongLength_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(1, Map.of(1, SHORT_KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void decode_whenKeyIsDifferent_throwsException() {
        var encoded = encryptionService.encode("secret-data");
        var otherService = new EncryptionService(new EncryptionProperties(1, Map.of(1, OTHER_KEY)));

        assertThatThrownBy(() -> otherService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void decode_whenKeyIdIsUnknown_throwsException() {
        // encoded by a service whose current key is id 2
        var producer = new EncryptionService(new EncryptionProperties(2, Map.of(2, KEY)));
        var encoded = producer.encode("secret-data");

        // the default service only knows key id 1
        assertThatThrownBy(() -> encryptionService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void rotation_whenNewKeyIsCurrent_stillDecodesDataWrittenWithOldKey() {
        var oldService = new EncryptionService(new EncryptionProperties(1, Map.of(1, KEY)));
        var encodedWithOldKey = oldService.encode("legacy-value");

        // rotation: key 2 becomes current, key 1 retained on the keyring for reads
        var rotatedService = new EncryptionService(new EncryptionProperties(2, Map.of(1, KEY, 2, NEW_KEY)));

        assertThat(rotatedService.decode(encodedWithOldKey)).isEqualTo("legacy-value");

        var encodedWithNewKey = rotatedService.encode("fresh-value");
        assertThat(encodedWithNewKey[0]).isEqualTo((byte) 2);
        assertThat(rotatedService.decode(encodedWithNewKey)).isEqualTo("fresh-value");
    }
}
