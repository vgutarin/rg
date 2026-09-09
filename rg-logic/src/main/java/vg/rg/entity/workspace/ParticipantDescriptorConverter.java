package vg.rg.entity.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.StringEncryptionConverter;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.service.EncryptionService;


/**
 * Persists a {@link ParticipantDescriptor} as one encrypted binary column: JSON on the way out,
 * {@link EncryptionService} over the result.
 *
 * <p>Follows {@link StringEncryptionConverter} — stateful, therefore resolved from Spring's managed-bean
 * registry rather than instantiated reflectively, which {@code @Component} plus the {@code vg.rg}
 * component scan arranges. That resolution path fails at runtime rather than at compile time, so it is
 * asserted by a persist/flush/clear/reload functional test.
 *
 * <p>The {@link ObjectMapper} is built here rather than injected, so that the two settings this format
 * depends on cannot drift with the application's serialization configuration. One of them is a Jackson 3
 * default today, which is exactly why it is still stated: a stored-format requirement must not rest on
 * a library default that a future upgrade may reverse.
 *
 * <ul>
 *   <li><strong>Unknown fields are ignored.</strong> A rollback to an older build must still be able to
 *       read rows a newer one wrote, which means an unrecognized key cannot be an error.</li>
 *   <li><strong>Nulls are omitted.</strong> A participant with no phone stores {@code {"v":1,"l":"…"}}
 *       instead of paying envelope space for {@code "p":null}. With lenient reads, absent and null
 *       arrive as the same thing, so nothing downstream has to tell them apart.</li>
 * </ul>
 *
 * <p>The size bound is enforced here as well as in the service. A bound applied only at the edge is not
 * a bound: this one also catches a caller that reaches the repository directly. The cost is an unfriendly
 * failure deep inside a flush, which is the right trade for a limit the database cannot check itself.
 */
@Component
@Converter
@RequiredArgsConstructor
public class ParticipantDescriptorConverter
        implements AttributeConverter<ParticipantDescriptor, byte[]> {

    /**
     * Shared, because a Jackson 3 mapper is immutable and therefore safe to reuse across every instance
     * of this converter — and configuration happens on the builder, since the 2.x setters are gone.
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(
                    inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final EncryptionService encryptionService;
    private final WorkspaceProperties workspaceProperties;

    @Override
    public byte[] convertToDatabaseColumn(ParticipantDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        final String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(descriptor);
        } catch (Exception exception) {
            // Deliberately without the value or the cause's message: either could carry the label or
            // the phone number into a log.
            throw new IllegalStateException("Failed to serialize a participant descriptor");
        }
        var sealed = encryptionService.encode(json);
        var limit = workspaceProperties.participantDescriptorMaxBytes();
        if (sealed.length > limit) {
            throw new IllegalStateException(
                    "Participant descriptor exceeds " + limit + " bytes");
        }
        return sealed;
    }

    @Override
    public ParticipantDescriptor convertToEntityAttribute(byte[] sealed) {
        if (sealed == null) {
            return null;
        }
        var json = encryptionService.decode(sealed);
        try {
            return OBJECT_MAPPER.readValue(json, ParticipantDescriptor.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read a participant descriptor");
        }
    }
}
