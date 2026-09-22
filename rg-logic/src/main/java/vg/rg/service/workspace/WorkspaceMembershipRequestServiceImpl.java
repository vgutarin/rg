package vg.rg.service.workspace;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.workspace.WorkspaceMembershipRequestEntity;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceMembershipApprovalTarget;
import vg.rg.model.workspace.WorkspaceMembershipRequestModel;
import vg.rg.repository.workspace.WorkspaceMembershipRequestRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.resource.ResourcePublicationService;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Objects;

/** Membership requests are identity-bound only after explicit requester consent and owner approval. */
@Service
@Slf4j
@RequiredArgsConstructor
class WorkspaceMembershipRequestServiceImpl implements WorkspaceMembershipRequestService {

    private static final String REQUEST_NOT_PENDING = "workspace.membership-request.error.not-pending";
    private static final String PARTICIPANT_NOT_AVAILABLE = "workspace.membership-request.error.participant-not-available";
    private static final String LABEL_REQUIRED = "workspace.participant.error.label-required";

    private final AuthorityChecker authorityChecker;
    private final ResourcePublicationService publicationService;
    private final WorkspaceMembershipRequestRepository requestRepository;
    private final WorkspaceParticipantRepository participantRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UniqueIdService uniqueIdService;
    private final WorkspaceProperties workspaceProperties;

    @Override
    @Transactional
    public void requestJoin(UniqueId eventId) {
        Objects.requireNonNull(eventId, "eventId");
        var principal = authorityChecker.currentPrincipal().orElseThrow(
                () -> new IllegalStateException("No authenticated user"));
        var user = Objects.requireNonNull(principal.userUniqueId(), "Authenticated user has no subject");
        var workspace = workspaceRepository.lockByEventUniqueId(eventId.getLongValue())
                .orElseThrow(EntityNotFoundException::new);
        if (!publicationService.isResourceVisibleForCurrentUser(eventId)) {
            throw new EntityNotFoundException();
        }
        var workspaceId = new UniqueId(workspace.getUniqueId());
        if (participantRepository.existsByWorkspaceUniqueIdAndUserUniqueId(workspaceId, user)
                || requestRepository.findByWorkspaceUniqueIdAndPendingUserUniqueId(workspaceId, user).isPresent()) {
            return;
        }
        requestRepository.saveWithNewUniqueId(WorkspaceMembershipRequestEntity.builder()
                .workspaceUniqueId(workspaceId)
                .pendingUserUniqueId(user)
                .originEventUniqueId(eventId)
                .descriptor(ParticipantDescriptor.of(principal.name(), null))
                .build(), uniqueIdService);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.WorkspaceParticipant.LIST + "')")
    public List<WorkspaceMembershipRequestModel> pending(UniqueId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return requestRepository.findByWorkspaceUniqueIdOrderByCreatedAtAsc(workspaceId)
                .stream().map(this::toModel).toList();
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.WorkspaceParticipant.CREATE + "')")
    public void approve(UniqueId workspaceId, UniqueId requestId, WorkspaceMembershipApprovalTarget target,
                        UniqueId existingParticipantId, String label) {
        Objects.requireNonNull(target, "target");
        var request = pendingRequest(workspaceId, requestId);
        var pendingUser = request.getPendingUserUniqueId();
        if (participantRepository.existsByWorkspaceUniqueIdAndUserUniqueId(workspaceId, pendingUser)) {
            throw new IllegalArgumentException(PARTICIPANT_NOT_AVAILABLE);
        }
        switch (target) {
            case CREATE_PARTICIPANT -> {
                var memberLabel = descriptorLabelOrNull(request.getDescriptor());
                if (memberLabel == null) {
                    memberLabel = validLabel(label);
                }
                ensureParticipantCapacity(workspaceId);
                participantRepository.saveWithNewUniqueId(WorkspaceParticipantEntity.builder()
                        .workspaceUniqueId(workspaceId)
                        .userUniqueId(pendingUser)
                        .descriptor(ParticipantDescriptor.of(memberLabel, null))
                        .build(), uniqueIdService);
            }
            case BIND_EXISTING_PARTICIPANT -> {
                if (existingParticipantId == null) {
                    throw new IllegalArgumentException(PARTICIPANT_NOT_AVAILABLE);
                }
                var participant = participantRepository.findById(existingParticipantId)
                        .filter(candidate -> workspaceId.equals(candidate.getWorkspaceUniqueId()))
                        .filter(candidate -> candidate.getUserUniqueId() == null)
                        .orElseThrow(() -> new IllegalArgumentException(PARTICIPANT_NOT_AVAILABLE));
                participant.setUserUniqueId(pendingUser);
                participantRepository.save(participant);
            }
        }
        deleteResolvedRequest(request, workspaceId, "approved");
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.Workspace.UPDATE + "')")
    public void reject(UniqueId workspaceId, UniqueId requestId) {
        deleteResolvedRequest(pendingRequest(workspaceId, requestId), workspaceId, "rejected");
    }

    private WorkspaceMembershipRequestEntity pendingRequest(UniqueId workspaceId, UniqueId requestId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(requestId, "requestId");
        var request = requestRepository.findById(requestId).orElseThrow(EntityNotFoundException::new);
        if (!workspaceId.equals(request.getWorkspaceUniqueId())) {
            throw new IllegalArgumentException(REQUEST_NOT_PENDING);
        }
        return request;
    }

    private void deleteResolvedRequest(
            WorkspaceMembershipRequestEntity request,
            UniqueId workspaceId,
            String decision
    ) {
        var decisionUser = authorityChecker.currentUserUniqueId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
        log.info("Membership request {} for workspace {} {} by user {}",
                request.getUniqueId(), workspaceId, decision, decisionUser);
        requestRepository.delete(request);
    }

    private String descriptorLabelOrNull(ParticipantDescriptor descriptor) {
        return labelOrNull(descriptor == null ? null : descriptor.label());
    }

    private String labelOrNull(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        return !normalized.isEmpty() && normalized.length() <= workspaceProperties.participantLabelMaxLength()
                ? normalized : null;
    }

    private String validLabel(String label) {
        if (labelOrNull(label) == null) {
            throw new IllegalArgumentException(LABEL_REQUIRED);
        }
        return label.trim();
    }

    private void ensureParticipantCapacity(UniqueId workspaceId) {
        var limit = workspaceProperties.participantsMaxPerWorkspace();
        if (participantRepository.countByWorkspaceUniqueId(workspaceId) >= limit) {
            throw new ParticipantLimitReachedException(limit);
        }
    }

    private WorkspaceMembershipRequestModel toModel(WorkspaceMembershipRequestEntity source) {
        return WorkspaceMembershipRequestModel.builder()
                .uniqueId(new UniqueId(source.getUniqueId()))
                .workspaceUniqueId(source.getWorkspaceUniqueId())
                .pendingUserUniqueId(source.getPendingUserUniqueId())
                .originEventUniqueId(source.getOriginEventUniqueId())
                .descriptor(source.getDescriptor())
                .createdAt(source.getCreatedAt())
                .version(source.getVersion())
                .build();
    }
}
