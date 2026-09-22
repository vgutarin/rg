package vg.rg.service.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.mapper.workspace.WorkspaceParticipantMapper;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.repository.workspace.WorkspaceEventRegistrationRepository;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceParticipantServiceImplTest {

    private static final UniqueId WORKSPACE = new UniqueId(8101L);

    @Mock UniqueIdService uniqueIdService;
    @Mock WorkspaceParticipantRepository repository;
    @Mock WorkspaceEventRegistrationRepository registrationRepository;
    @Mock WorkspaceProperties workspaceProperties;

    @Test
    void browseUnbound_filtersBeforeSortingAndPaging() {
        when(repository.findByWorkspaceUniqueId(WORKSPACE)).thenReturn(List.of(
                participant(1L, "Alpha", new UniqueId(9101L)),
                participant(2L, "Charlie", null),
                participant(3L, "Bravo", null)));
        var service = new WorkspaceParticipantServiceImpl(uniqueIdService, repository,
                registrationRepository, new WorkspaceParticipantMapper(), workspaceProperties);

        var page = service.browseUnbound(WORKSPACE, null, PageRequest.of(0, 1));

        assertThat(page.getContent()).extracting(WorkspaceParticipantModel::getLabel)
                .containsExactly("Bravo");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private static WorkspaceParticipantEntity participant(Long id, String label, UniqueId userId) {
        return WorkspaceParticipantEntity.builder().uniqueId(id).workspaceUniqueId(WORKSPACE)
                .userUniqueId(userId).descriptor(ParticipantDescriptor.of(label, null)).build();
    }
}
