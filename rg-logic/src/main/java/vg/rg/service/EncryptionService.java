package vg.rg.service;

import org.springframework.stereotype.Component;
import vg.rg.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * AES-256/GCM encryption for individual persisted fields, applied through
 * {@code vg.rg.entity.StringEncryptionConverter}.
 *
 * <p>A ciphertext is laid out as {@code [keyId:1][iv:12][ciphertext+tag]}. Stamping the key id means a
 * value stays readable after its key stops being the current one, which is what makes rotation possible
 * without rewriting existing rows. Note the corollary: <strong>rotation is read-compatibility only</strong>
 * — there is no re-encryption job, so a row keeps the key it was written with until something rewrites
 * it, and its key can never be dropped from the keyring.
 *
 * <p>The key id and IV sit outside GCM's authenticated data, and the layout carries no version byte.
 * Changing the format later therefore needs an explicit migration rather than a discriminator.
 *
 * <p>The configuration is validated here, at construction, so a missing or malformed key fails startup
 * rather than the first write.
 */
@Component
public final class EncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_ID_LENGTH = 1;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final int MAX_KEY_ID = 0xFF;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<Integer, SecretKey> keyring;
    private final int currentKeyId;
    private final SecretKey currentKey;

    public EncryptionService(EncryptionProperties properties) {
        Objects.requireNonNull(properties, "EncryptionProperties must not be null");
        if (properties.keys() == null || properties.keys().isEmpty()) {
            throw new IllegalStateException("rg.encryption.keys is not configured!");
        }
        if (properties.currentKeyId() == null) {
            throw new IllegalStateException("rg.encryption.current-key-id is not configured!");
        }

        var ring = new HashMap<Integer, SecretKey>();
        properties.keys().forEach((id, key) -> {
            if (id < 0 || id > MAX_KEY_ID) {
                throw new IllegalStateException("Encryption key id must be in range 0.." + MAX_KEY_ID + ", got: " + id);
            }
            ring.put(id, parseKey(id, key));
        });

        this.currentKeyId = properties.currentKeyId();
        if (!ring.containsKey(currentKeyId)) {
            throw new IllegalStateException(
                    "rg.encryption.current-key-id " + currentKeyId + " is not present in rg.encryption.keys");
        }

        this.keyring = Map.copyOf(ring);
        this.currentKey = this.keyring.get(currentKeyId);
    }

    /**
     * Encrypts a field value with the current key. Returns {@code null} for {@code null} input, so a
     * nullable column stays nullable.
     */
    public byte[] encode(String src) {
        if (src == null) {
            return null;
        }
        try {
            var iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            var cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            var encrypted = cipher.doFinal(src.getBytes(UTF_8));

            return ByteBuffer.allocate(KEY_ID_LENGTH + iv.length + encrypted.length)
                    .put((byte) currentKeyId)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt field value", e);
        }
    }

    /**
     * Decrypts a field value using the key its first byte names, which may be an older key than the
     * current one.
     */
    public String decode(byte[] encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            var keyId = encoded[0] & 0xFF;
            var key = keyring.get(keyId);
            if (key == null) {
                throw new IllegalStateException("No encryption key configured for key id " + keyId);
            }

            var iv = Arrays.copyOfRange(encoded, KEY_ID_LENGTH, KEY_ID_LENGTH + IV_LENGTH);
            var ciphertext = Arrays.copyOfRange(encoded, KEY_ID_LENGTH + IV_LENGTH, encoded.length);

            var cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt field value", e);
        }
    }

    private static SecretKey parseKey(int id, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Encryption key id " + id + " is not configured!");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encryption key id " + id + " is not valid Base64", e);
        }
        if (raw.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Encryption key id " + id + " must decode to " + AES_KEY_LENGTH_BYTES + " bytes, got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
