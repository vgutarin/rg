package vg.rg.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vg.rg.service.EncryptionService;

/**
 * Encrypts a {@code String} attribute into a binary column and back.
 *
 * <p>Unlike {@code UniqueIdLongConverter}, this converter is stateful: it holds an
 * {@link EncryptionService} and so must be resolved as a Spring bean through Hibernate's managed-bean
 * registry rather than instantiated reflectively. {@code @Component} plus the application's
 * {@code vg.rg} component scan is what arranges that.
 */
@Component
@Converter
@RequiredArgsConstructor
public class StringEncryptionConverter implements AttributeConverter<String, byte[]> {

    private final EncryptionService encryptionService;

    @Override
    public byte[] convertToDatabaseColumn(String src) {
        if (src == null) {
            return null;
        }
        return encryptionService.encode(src);
    }

    @Override
    public String convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return encryptionService.decode(bytes);
    }
}
