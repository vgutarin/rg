package vg.rg.entity.workspace;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.BaseFuncTest;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that proves field encryption actually works through Hibernate, which nothing could do until
 * some entity carried the annotation.
 *
 * <p>{@code EncryptionWiringFuncTest} covers what can be covered without a use — the properties bind, the
 * configured key round-trips, the converter is resolvable as a bean — but not the step that matters
 * most: a <strong>stateful</strong> converter has to be obtained from Spring's managed-bean registry
 * rather than instantiated reflectively, and if Hibernate cannot do that it fails at runtime, not at
 * compile time. {@link WorkspaceParticipantEntity} is the first entity to carry one, so this closes the
 * gap recorded in {@code specs/current/open-decisions.md}.
 *
 * <p>Two assertions, and neither is redundant. The reload proves the converter resolved and both
 * directions ran. The raw JDBC read proves the column really holds ciphertext — a round trip alone would
 * pass just as happily if the converter stored plaintext.
 */
class ParticipantDescriptorPersistenceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(4201L);

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceParticipantRepository participantRepository;

    @Autowired
    private UniqueIdService uniqueIdService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UniqueId workspaceId;

    @BeforeEach
    void setUp() {
        // Auditing fills author from the current principal, and the column is NOT NULL.
        authenticate();
        var workspace = workspaceRepository.saveWithNewUniqueId(
                WorkspaceEntity.builder().ownerUniqueId(OWNER).build(), uniqueIdService);
        workspaceId = new UniqueId(workspace.getUniqueId());
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    void descriptor_survivesAFlushClearAndReload() {
        var descriptor = ParticipantDescriptor.of("Ivan the plumber", "+380501112233");
        var saved = participantRepository.saveWithNewUniqueId(
                WorkspaceParticipantEntity.builder()
                        .workspaceUniqueId(workspaceId)
                        .descriptor(descriptor)
                        .build(),
                uniqueIdService);

        entityManager.flush();
        // Without the clear, the assertion below would read the same instance out of the persistence
        // context and never exercise the converter's read direction at all.
        entityManager.clear();

        var reloaded = entityManager.find(WorkspaceParticipantEntity.class, saved.getUniqueId());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getDescriptor()).isEqualTo(descriptor);
        assertThat(reloaded.getWorkspaceUniqueId()).isEqualTo(workspaceId);
        assertThat(reloaded.getUserUniqueId()).isNull();
    }

    @Test
    void storedColumn_holdsCiphertextAndNotAPerson() {
        var saved = participantRepository.saveWithNewUniqueId(
                WorkspaceParticipantEntity.builder()
                        .workspaceUniqueId(workspaceId)
                        .descriptor(ParticipantDescriptor.of("Ivan Petrenko", "+380501112233"))
                        .build(),
                uniqueIdService);

        var stored = jdbcTemplate.queryForObject(
                "select descriptor from rg_workspace_participant where unique_id = ?",
                byte[].class, saved.getUniqueId());

        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.UTF_8))
                .doesNotContain("Ivan")
                .doesNotContain("Petrenko")
                .doesNotContain("380501112233");
    }

    /**
     * The entity's {@code toString()} is the accident waiting to happen: this family of entities carries
     * Lombok's {@code @ToString}, so without {@code @ToString.Exclude} on the descriptor a single log
     * line printing an entity would disclose the label and the number.
     */
    @Test
    void entityToString_excludesTheDescriptor() {
        var entity = WorkspaceParticipantEntity.builder()
                .workspaceUniqueId(workspaceId)
                .descriptor(ParticipantDescriptor.of("Ivan Petrenko", "+380501112233"))
                .build();

        assertThat(entity.toString())
                .doesNotContain("Ivan")
                .doesNotContain("Petrenko")
                .doesNotContain("380501112233")
                .doesNotContain("descriptor");
    }

    private static void authenticate() {
        var principal = new AuthenticatedUserPrincipal(
                OWNER, "Test User",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
