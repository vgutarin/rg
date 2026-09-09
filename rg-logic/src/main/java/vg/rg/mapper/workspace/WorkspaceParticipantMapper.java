package vg.rg.mapper.workspace;

import org.springframework.stereotype.Component;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.unique.id.model.UniqueId;

/**
 * Maps a participant entity onto its read model.
 *
 * <p><strong>Hand-written, unlike {@link WorkspaceLocationMapper} and {@link WorkspaceMapper}.</strong>
 * This mapping is the point at which it is decided what leaves the business layer about a natural
 * person, and it must be readable as such at a glance. Expressing "take the label but not the phone, and
 * derive a suffix from the number" through MapStruct would mean three {@code expression = "java(...)"}
 * strings that the compiler does not check until code generation — the wrong place for a rule whose
 * failure mode is disclosing a phone number.
 *
 * <p>There is deliberately no {@code toEntity}. The descriptor is assembled by the service from the
 * caller's input, and the workspace comes from the operation rather than from any model, which is what
 * makes a participant's workspace unforgeable from the UI.
 */
@Component
public class WorkspaceParticipantMapper {

    /**
     * The roster view of a participant: the label, and whether a number exists — never the number.
     */
    public WorkspaceParticipantModel toModel(WorkspaceParticipantEntity src) {
        if (src == null) {
            return null;
        }
        var descriptor = src.getDescriptor();
        var phone = descriptor == null ? null : descriptor.phone();
        return WorkspaceParticipantModel.builder()
                .uniqueId(src.getUniqueId() == null ? null : new UniqueId(src.getUniqueId()))
                .userUniqueId(src.getUserUniqueId())
                .label(descriptor == null ? null : descriptor.label())
                .phoneRecorded(phone != null)
                .author(src.getAuthor())
                .lastEditor(src.getLastEditor())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .version(src.getVersion())
                .build();
    }
}
