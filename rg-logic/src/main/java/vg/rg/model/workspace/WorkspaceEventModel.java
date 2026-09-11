package vg.rg.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/** The editable event content and its audit metadata. */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class WorkspaceEventModel implements Identifiable {

    private UniqueId uniqueId;
    private String title;
    private Instant startAt;
    private Instant endAt;
    private UniqueId locationUniqueId;
    private WorkspaceEventType eventType;
    private Integer maxParticipantCount;
    private boolean isPublished;
    private UniqueId author;
    private UniqueId lastEditor;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;
}
