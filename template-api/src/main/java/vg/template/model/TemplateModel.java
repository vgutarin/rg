package vg.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * Represents some model
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TemplateModel implements Identifiable {

    private UniqueId uniqueId;
    private String name;
    private String description;

    private Instant createdAt;
    private Instant updatedAt;
    private int version;
}
