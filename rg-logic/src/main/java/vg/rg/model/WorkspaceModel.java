package vg.rg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * A workspace as the presentation layer sees it.
 *
 * <p>{@code name} is <strong>nullable</strong>: null means the workspace was created by the system — the
 * auto-provisioned default, or one the migration created — and its label must be rendered from a message
 * key so it follows the viewer's locale. A user-supplied name is stored and shown verbatim in every
 * locale. {@code defaultWorkspace} is a read-only projection.
 *
 * <p>The owner's identity is deliberately absent: it is scope and audit data, never presentation input.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceModel implements Identifiable {

    private UniqueId uniqueId;
    private String name;
    private String description;
    private boolean defaultWorkspace;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;

    /** Whether the label must be resolved from a message key rather than from {@link #name}. */
    public boolean isSystemNamed() {
        return name == null;
    }
}
