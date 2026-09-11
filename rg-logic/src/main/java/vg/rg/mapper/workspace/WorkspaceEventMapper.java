package vg.rg.mapper.workspace;

import org.springframework.stereotype.Component;
import vg.rg.entity.workspace.WorkspaceEventEntity;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.unique.id.model.UniqueId;

/** Maps event persistence state without exposing its workspace containment as editable content. */
@Component
public class WorkspaceEventMapper {

    public WorkspaceEventModel toModel(WorkspaceEventEntity source) {
        if (source == null) {
            return null;
        }
        return WorkspaceEventModel.builder()
                .uniqueId(source.getUniqueId() == null ? null : new UniqueId(source.getUniqueId()))
                .title(source.getTitle())
                .startAt(source.getStartAt())
                .endAt(source.getEndAt())
                .locationUniqueId(source.getLocationUniqueId())
                .eventType(source.getEventType())
                .maxParticipantCount(source.getMaxParticipantCount())
                .isPublished(source.isPublished())
                .author(source.getAuthor())
                .lastEditor(source.getLastEditor())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .version(source.getVersion())
                .build();
    }
}
