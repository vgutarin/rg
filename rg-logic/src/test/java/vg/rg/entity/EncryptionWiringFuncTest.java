package vg.rg.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.rg.BaseFuncTest;
import vg.rg.config.EncryptionProperties;
import vg.rg.service.EncryptionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the three things about field encryption that only a real Spring context can show, and that the
 * unit tests cannot: that {@link EncryptionProperties} binds from configuration at all — it is the one
 * class here using {@code @ConfigurationProperties} constructor binding, so a mis-registered record
 * would leave the keyring null — that the configured key material is actually usable, and that
 * {@link StringEncryptionConverter} is resolvable as a bean.
 *
 * <p>The bean check matters because this converter is stateful, unlike {@code UniqueIdLongConverter}:
 * Hibernate must obtain it from Spring's managed-bean registry rather than instantiate it reflectively,
 * and that failure surfaces at runtime rather than at compile time.
 *
 * <p>What this deliberately does <strong>not</strong> prove is the Hibernate resolution path itself,
 * which only runs once an entity actually carries the annotation. That is now covered — by
 * {@link ParticipantDescriptorPersistenceFuncTest}, over the first encrypted column in the schema. No
 * entity carries {@code StringEncryptionConverter} specifically, so the checks here remain the only
 * coverage <em>that</em> converter has.
 */
class EncryptionWiringFuncTest extends BaseFuncTest {

    @Autowired
    private EncryptionProperties encryptionProperties;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private StringEncryptionConverter converter;

    @Test
    void properties_bindFromConfiguration() {
        assertThat(encryptionProperties.currentKeyId()).isNotNull();
        assertThat(encryptionProperties.keys()).containsKey(encryptionProperties.currentKeyId());
    }

    @Test
    void converter_isWiredWithTheConfiguredService() {
        var plaintext = "wired-through-spring";

        var stored = converter.convertToDatabaseColumn(plaintext);

        assertThat(stored).isNotNull();
        assertThat(stored[0]).isEqualTo(encryptionProperties.currentKeyId().byteValue());
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plaintext);
    }

    @Test
    void configuredKeyMaterial_roundTripsThroughTheService() {
        var plaintext = "configured-key-works";

        assertThat(encryptionService.decode(encryptionService.encode(plaintext))).isEqualTo(plaintext);
    }
}
