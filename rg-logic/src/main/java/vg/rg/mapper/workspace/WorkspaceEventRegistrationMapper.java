package vg.rg.mapper.workspace;

import org.springframework.stereotype.Component;
import vg.rg.entity.workspace.WorkspaceEventRegistrationEntity;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.unique.id.model.UniqueId;

/**
 * Maps a registration entity onto its read model.
 *
 * <p>The participant label is not on the entity — it lives, encrypted, on the participant — so it is
 * supplied separately by the service, which is the only layer that may open a descriptor. The plain
 * mapping leaves {@code participantLabel} null; {@link #toModel(WorkspaceEventRegistrationEntity, String)}
 * fills it.
 */
@Component
public class WorkspaceEventRegistrationMapper {

    public WorkspaceEventRegistrationModel toModel(WorkspaceEventRegistrationEntity source) {
        return toModel(source, null);
    }

    public WorkspaceEventRegistrationModel toModel(
            WorkspaceEventRegistrationEntity source, String participantLabel) {
        if (source == null) {
            return null;
        }
        return WorkspaceEventRegistrationModel.builder()
                .uniqueId(source.getUniqueId() == null ? null : new UniqueId(source.getUniqueId()))
                .eventUniqueId(source.getEventUniqueId())
                .participantUniqueId(source.getParticipantUniqueId())
                .participantLabel(participantLabel)
                .orderBy(source.getOrderBy())
                .author(source.getAuthor())
                .lastEditor(source.getLastEditor())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .version(source.getVersion())
                .build();
    }
}
