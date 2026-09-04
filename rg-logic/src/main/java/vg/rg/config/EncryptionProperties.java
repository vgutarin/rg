package vg.rg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Application-owned configuration for field-level encryption at rest.
 *
 * <p>Unlike {@link GeoProperties} and {@link WorkspaceProperties}, this class binds through
 * {@code @ConfigurationProperties} rather than reading {@link org.springframework.core.env.Environment}
 * directly: {@link #keys} is a map, which the manual accessor style cannot bind. Binding also brings
 * relaxed property matching, so a deployment can supply {@code RG_ENCRYPTION_KEYS_0} as an environment
 * variable with no extra code.
 *
 * <p>Deliberately a record, so the bound key material is immutable — nothing in the context can
 * reassign the keyring after startup — and deliberately without defaults: unlike a search radius, a
 * missing encryption key has no safe fallback, and {@code EncryptionService} rejects an incomplete
 * configuration at construction time.
 *
 * @param currentKeyId id of the key that encrypts new data. Must be present in {@link #keys} and in
 *                     range 0..255. To rotate, add a new entry to {@link #keys} and point this at it;
 *                     old entries stay on the keyring so data written with them remains readable.
 * @param keys         keyring: key id -&gt; Base64-encoded 32-byte AES-256 key
 *                     ({@code openssl rand -base64 32}). The value is raw key material, used with no
 *                     derivation. Every ciphertext is stamped with the id of the key that produced it,
 *                     so several keys can coexist during a rotation.
 */
@ConfigurationProperties("rg.encryption")
public record EncryptionProperties(Integer currentKeyId, Map<Integer, String> keys) {
}
