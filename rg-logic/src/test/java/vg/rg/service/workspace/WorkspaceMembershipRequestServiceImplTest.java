package vg.rg.service.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.workspace.WorkspaceMembershipRequestEntity;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceMembershipApprovalTarget;
import vg.rg.repository.workspace.WorkspaceMembershipRequestRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.resource.ResourcePublicationService;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceMembershipRequestServiceImplTest {

    private static final UniqueId EVENT = new UniqueId(7101L);
    private static final UniqueId WORKSPACE = new UniqueId(7102L);
    private static final UniqueId USER = new UniqueId(7103L);

    @Mock AuthorityChecker authorityChecker;
    @Mock ResourcePublicationService publicationService;
    @Mock WorkspaceMembershipRequestRepository requestRepository;
    @Mock WorkspaceParticipantRepository participantRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock UniqueIdService uniqueIdService;
    @Mock WorkspaceProperties workspaceProperties;

    private WorkspaceMembershipRequestService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceMembershipRequestServiceImpl(authorityChecker, publicationService,
                requestRepository, participantRepository, workspaceRepository, uniqueIdService, workspaceProperties);
        when(authorityChecker.currentPrincipal()).thenReturn(Optional.of(new AuthenticatedUserPrincipal(
                USER, "Requester", Set.of(), true, AuthenticationFlow.TELEGRAM)));
        when(publicationService.isResourceVisibleForCurrentUser(EVENT)).thenReturn(true);
        when(workspaceRepository.lockByEventUniqueId(EVENT.getLongValue())).thenReturn(Optional.of(
                WorkspaceEntity.builder().uniqueId(WORKSPACE.getLongValue()).build()));
        when(participantRepository.existsByWorkspaceUniqueIdAndUserUniqueId(WORKSPACE, USER)).thenReturn(false);
        when(requestRepository.findByWorkspaceUniqueIdAndPendingUserUniqueId(WORKSPACE, USER))
                .thenReturn(Optional.empty());
    }

    @Test
    void requestJoin_createsOnePendingRequestBoundToTheAuthenticatedUser() {
        service.requestJoin(EVENT);

        var request = ArgumentCaptor.forClass(WorkspaceMembershipRequestEntity.class);
        verify(requestRepository).saveWithNewUniqueId(request.capture(), eq(uniqueIdService));
        assertThat(request.getValue()).extracting(
                WorkspaceMembershipRequestEntity::getWorkspaceUniqueId,
                WorkspaceMembershipRequestEntity::getPendingUserUniqueId,
                WorkspaceMembershipRequestEntity::getOriginEventUniqueId,
                WorkspaceMembershipRequestEntity::getDescriptor)
                .containsExactly(WORKSPACE, USER, EVENT, ParticipantDescriptor.of("Requester", null));
    }

    @Test
    void requestJoin_rejectsAnEventThatIsNotVisibleToTheCurrentUser() {
        when(publicationService.isResourceVisibleForCurrentUser(EVENT)).thenReturn(false);

        assertThatThrownBy(() -> service.requestJoin(EVENT)).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        verify(requestRepository, never()).saveWithNewUniqueId(any(), any());
    }

    @Test
    void approve_withNoUsableRequesterDescriptorLabel_requiresAnOwnerLabelAndNeverMatchesByName() {
        var request = pendingRequest(null);
        when(requestRepository.findById(EVENT)).thenReturn(Optional.of(request));
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(new UniqueId(7104L)));
        when(workspaceProperties.participantLabelMaxLength()).thenReturn(128);

        assertThatThrownBy(() -> service.approve(WORKSPACE, EVENT,
                WorkspaceMembershipApprovalTarget.CREATE_PARTICIPANT, null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace.participant.error.label-required");
    }

    @Test
    void approve_createsABoundParticipantFromATrimmedRequesterDescriptorLabel() {
        var request = pendingRequest("  Requester  ");
        when(requestRepository.findById(EVENT)).thenReturn(Optional.of(request));
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(new UniqueId(7104L)));
        when(workspaceProperties.participantLabelMaxLength()).thenReturn(128);
        when(workspaceProperties.participantsMaxPerWorkspace()).thenReturn(20);

        service.approve(WORKSPACE, EVENT, WorkspaceMembershipApprovalTarget.CREATE_PARTICIPANT, null, "ignored");

        var participant = ArgumentCaptor.forClass(vg.rg.entity.workspace.WorkspaceParticipantEntity.class);
        verify(participantRepository).saveWithNewUniqueId(participant.capture(), eq(uniqueIdService));
        assertThat(participant.getValue().getUserUniqueId()).isEqualTo(USER);
        assertThat(participant.getValue().getDescriptor().label()).isEqualTo("Requester");
        verify(requestRepository).delete(request);
    }

    @Test
    void approve_createTargetRejectsAFullRosterWithoutChangingTheRequest() {
        var request = pendingRequest("Requester");
        when(requestRepository.findById(EVENT)).thenReturn(Optional.of(request));
        when(workspaceProperties.participantLabelMaxLength()).thenReturn(128);
        when(workspaceProperties.participantsMaxPerWorkspace()).thenReturn(2);
        when(participantRepository.countByWorkspaceUniqueId(WORKSPACE)).thenReturn(2L);

        assertThatThrownBy(() -> service.approve(WORKSPACE, EVENT,
                WorkspaceMembershipApprovalTarget.CREATE_PARTICIPANT, null, "ignored"))
                .isInstanceOf(ParticipantLimitReachedException.class)
                .hasMessage(ParticipantLimitReachedException.MESSAGE_KEY);

        verify(participantRepository, never()).saveWithNewUniqueId(any(), any());
        verify(requestRepository, never()).delete(request);
    }

    @Test
    void approve_withExistingTargetButNoSelection_isRejectedRatherThanCreatingAParticipant() {
        var request = pendingRequest("Requester");
        when(requestRepository.findById(EVENT)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(WORKSPACE, EVENT,
                WorkspaceMembershipApprovalTarget.BIND_EXISTING_PARTICIPANT, null, "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace.membership-request.error.participant-not-available");
    }

    @Test
    void reject_deletesTheRequestAndMakesTheUserEligibleToApplyAgain() {
        var request = pendingRequest("Requester");
        when(requestRepository.findById(EVENT)).thenReturn(Optional.of(request));
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(new UniqueId(7104L)));

        service.reject(WORKSPACE, EVENT);

        verify(requestRepository).delete(request);
    }

    private static WorkspaceMembershipRequestEntity pendingRequest(String name) {
        return WorkspaceMembershipRequestEntity.builder().uniqueId(EVENT.getLongValue())
                .workspaceUniqueId(WORKSPACE).pendingUserUniqueId(USER)
                .descriptor(name == null ? null : ParticipantDescriptor.of(name, null))
                .build();
    }
}
