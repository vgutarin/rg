package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.model.ParticipantDescriptor;
import vg.rg.model.WorkspaceModel;
import vg.rg.model.WorkspaceParticipantModel;
import vg.rg.repository.WorkspaceParticipantRepository;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-backed coverage of {@link WorkspaceParticipantService} against MySQL, end to end through the real
 * authority boundary, the real workspace-scope join and the real encrypted column.
 *
 * <p>Two properties carry most of the weight. <strong>Isolation</strong>: a roster read must never
 * return another workspace's people, and a participant must not be reachable from a workspace that does
 * not contain them. <strong>Separation of reading from disclosing</strong>: the roster never carries a
 * phone number, and the only method that produces one is guarded by its own permission.
 *
 * <p>The owner here holds nothing but the app-wide {@code workspace:owner} gate — no
 * {@code workspace-participant:*} capability anywhere in this test — which is what demonstrates that
 * owning the workspace is by itself sufficient inside it, and that adding this type required no change
 * to the authority model.
 */
class WorkspaceParticipantServiceFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(4301L);
    private static final UniqueId STRANGER = new UniqueId(4302L);
    private static final String PHONE = "+380501112233";

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceParticipantService service;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceParticipantRepository participantRepository;

    private UniqueId workspaceA;
    private UniqueId workspaceB;

    @BeforeEach
    void setUp() {
        authenticate(OWNER);
        workspaceA = workspaceService.create(WorkspaceModel.builder().name("Alpha").build()).getUniqueId();
        workspaceB = workspaceService.create(WorkspaceModel.builder().name("Beta").build()).getUniqueId();
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ registering and listing

    @Test
    void registeredParticipant_isListedInItsWorkspaceOnly() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan the plumber", PHONE));

        assertThat(created.getUniqueId()).isNotNull();
        // Never bound to an identity subject by registration: this person has not authenticated, and
        // may never do so.
        assertThat(created.getUserUniqueId()).isNull();
        assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent())
                .extracting(WorkspaceParticipantModel::getLabel)
                .containsExactly("Ivan the plumber");
        assertThat(service.browse(workspaceB, null, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    /**
     * The roster carries whether a number exists and has nowhere to put the number itself — which is
     * what makes the split between reading and disclosing structural rather than a convention callers
     * must remember.
     */
    @Test
    void roster_reportsThePhoneWithoutCarryingIt() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        var listed = service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent().getFirst();

        assertThat(listed.isPhoneRecorded()).isTrue();
        // Not merely absent from a getter: no rendering of the model may contain the number at all.
        assertThat(listed.toString()).doesNotContain(PHONE).doesNotContain("2233");
    }

    @Test
    void participantWithoutAPhone_isRecordedAsHavingNone() {
        service.register(workspaceA, ParticipantDescriptor.of("Label only", null));

        var listed = service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent().getFirst();

        assertThat(listed.isPhoneRecorded()).isFalse();
    }

    @Test
    void register_normalizesThePhoneBeforeStoringIt() {
        var created = service.register(
                workspaceA, ParticipantDescriptor.of("Ivan", "+380 (50) 111-22-33"));

        assertThat(service.revealContact(created.getUniqueId()).phone()).isEqualTo(PHONE);
    }

    @Test
    void register_stampsTheCurrentSchemaVersionRegardlessOfWhatTheCallerPassed() {
        var created = service.register(
                workspaceA, new ParticipantDescriptor(99, "Ivan", PHONE));

        assertThat(service.revealContact(created.getUniqueId()).schemaVersion())
                .isEqualTo(ParticipantDescriptor.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void register_rejectsAMissingOrOversizedLabel() {
        assertThatThrownBy(() -> service.register(workspaceA, ParticipantDescriptor.of("  ", PHONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace.participant.error.label-required");
        assertThatThrownBy(() -> service.register(
                workspaceA, ParticipantDescriptor.of("x".repeat(129), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace.participant.error.label-too-long");
    }

    // ------------------------------------------------------------------- ordering and paging

    @Test
    void roster_isOrderedByLabel_notByInsertion() {
        service.register(workspaceA, ParticipantDescriptor.of("Zoryana", null));
        service.register(workspaceA, ParticipantDescriptor.of("Andriy", null));
        service.register(workspaceA, ParticipantDescriptor.of("Mykola", null));

        assertThat(labels(service.browse(workspaceA, null, PageRequest.of(0, 10))))
                .containsExactly("Andriy", "Mykola", "Zoryana");
    }

    /**
     * Natural {@code String} order would put every uppercase label ahead of every lowercase one, so
     * "Zoe" would sort before "alice" — visibly broken to anyone reading the list.
     */
    @Test
    void ordering_isCaseInsensitive() {
        service.register(workspaceA, ParticipantDescriptor.of("zoe", null));
        service.register(workspaceA, ParticipantDescriptor.of("Alice", null));
        service.register(workspaceA, ParticipantDescriptor.of("bob", null));

        assertThat(labels(service.browse(workspaceA, null, PageRequest.of(0, 10))))
                .containsExactly("Alice", "bob", "zoe");
    }

    /**
     * Case-insensitive comparison makes these three <em>equal</em>, so the identifier tie-break is what
     * keeps the order total — and therefore what keeps page boundaries repeatable.
     */
    @Test
    void equalLabels_areOrderedByIdentifier_repeatably() {
        var first = service.register(workspaceA, ParticipantDescriptor.of("Ivan", "+380501110001"));
        var second = service.register(workspaceA, ParticipantDescriptor.of("ivan", "+380501110002"));
        var third = service.register(workspaceA, ParticipantDescriptor.of("IVAN", "+380501110003"));

        var expected = java.util.stream.Stream.of(first, second, third)
                .map(WorkspaceParticipantModel::getUniqueId)
                .sorted()
                .toList();

        for (var attempt = 0; attempt < 3; attempt++) {
            assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent())
                    .extracting(WorkspaceParticipantModel::getUniqueId)
                    .containsExactlyElementsOf(expected);
        }
    }

    /**
     * The one silent regression available here is slicing before ordering — it would return an
     * arbitrary subset instead of the alphabetically first one. The labels are registered in the
     * reverse of their alphabetical order so that the two cannot be confused.
     */
    @Test
    void pagesAreSlicedAfterOrdering_soTheyContinueAlphabetically() {
        for (var label : List.of("Elena", "Dmytro", "Chrystyna", "Bohdan", "Anna")) {
            service.register(workspaceA, ParticipantDescriptor.of(label, null));
        }

        var first = service.browse(workspaceA, null, PageRequest.of(0, 2));
        var second = service.browse(workspaceA, null, PageRequest.of(1, 2));
        var third = service.browse(workspaceA, null, PageRequest.of(2, 2));

        assertThat(labels(first)).containsExactly("Anna", "Bohdan");
        assertThat(labels(second)).containsExactly("Chrystyna", "Dmytro");
        assertThat(labels(third)).containsExactly("Elena");
        // No overlap and no gap: the three pages reassemble into the whole ordered roster.
        assertThat(java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.getContent().stream())
                .map(WorkspaceParticipantModel::getLabel))
                .containsExactly("Anna", "Bohdan", "Chrystyna", "Dmytro", "Elena");
    }

    @Test
    void total_isTheFilteredSize_notThePageSizeAndNotTheWholeRoster() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan the plumber", PHONE));
        service.register(workspaceA, ParticipantDescriptor.of("Ivan the vet", "+380501114455"));
        service.register(workspaceA, ParticipantDescriptor.of("Olena", "+380501116677"));

        var unfiltered = service.browse(workspaceA, null, PageRequest.of(0, 1));
        assertThat(unfiltered.getContent()).hasSize(1);
        assertThat(unfiltered.getTotalElements()).isEqualTo(3);

        var filtered = service.browse(workspaceA, "ivan", PageRequest.of(0, 1));
        assertThat(filtered.getContent()).hasSize(1);
        assertThat(filtered.getTotalElements()).isEqualTo(2);
    }

    /**
     * The filter cannot be a SQL search: the label is encrypted with a fresh IV per write, so the
     * database can neither match nor index it, and the service opens every descriptor to compare.
     */
    @Test
    void filter_matchesCaseInsensitivelyWithinTheWorkspaceOnly() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan the plumber", PHONE));
        service.register(workspaceA, ParticipantDescriptor.of("Olena the vet", "+380501114455"));
        service.register(workspaceB, ParticipantDescriptor.of("Ivan elsewhere", "+380501116677"));

        assertThat(labels(service.browse(workspaceA, "IVAN", PageRequest.of(0, 10))))
                .containsExactly("Ivan the plumber");
        // Filtered results are ordered too, not merely selected.
        assertThat(labels(service.browse(workspaceA, "the", PageRequest.of(0, 10))))
                .containsExactly("Ivan the plumber", "Olena the vet");
    }

    @Test
    void blankFilter_returnsTheWholeRoster() {
        service.register(workspaceA, ParticipantDescriptor.of("First", null));
        service.register(workspaceA, ParticipantDescriptor.of("Second", null));

        assertThat(service.browse(workspaceA, "   ", PageRequest.of(0, 10)).getContent()).hasSize(2);
        assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent()).hasSize(2);
    }

    @Test
    void filter_noMatch_isEmptyRatherThanEverything() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        var page = service.browse(workspaceA, "nobody", PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    /** Results carry no phone number, exactly as the roster does not — same model, same guarantee. */
    @Test
    void results_carryNoNumber() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        var found = service.browse(workspaceA, "ivan", PageRequest.of(0, 10)).getContent().getFirst();

        assertThat(found.isPhoneRecorded()).isTrue();
        assertThat(found.toString()).doesNotContain(PHONE);
    }

    /**
     * A sort cannot be honoured — the ordering is fixed — so it is refused rather than dropped. A
     * caller that adds a sortable column should fail here and implement it, not silently receive data
     * in an order it did not ask for.
     */
    @Test
    void aPageableCarryingASort_isRefused() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", null));

        assertThatThrownBy(() -> service.browse(workspaceA, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workspace.participant.error.sort-not-supported");
    }

    @Test
    void anOffsetPastTheEnd_isAnEmptyPageRatherThanAFailure() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", null));

        var page = service.browse(workspaceA, null, PageRequest.of(9, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    private static List<String> labels(org.springframework.data.domain.Page<WorkspaceParticipantModel> page) {
        return page.getContent().stream().map(WorkspaceParticipantModel::getLabel).toList();
    }

    // ------------------------------------------------------------------------------- disclosure

    @Test
    void revealContact_isTheOnlyWayToObtainThePlaintext() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        var revealed = service.revealContact(created.getUniqueId());

        assertThat(revealed.label()).isEqualTo("Ivan");
        assertThat(revealed.phone()).isEqualTo(PHONE);
    }

    // -------------------------------------------------------------------- duplicate detection

    @Test
    void register_refusesANumberThisWorkspaceAlreadyLists() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        // A different spelling of the same number, to prove the comparison happens after normalization.
        assertThatThrownBy(() -> service.register(
                workspaceA, ParticipantDescriptor.of("Ivan again", "+380 50 111 2233")))
                .isInstanceOf(DuplicateParticipantPhoneException.class);
    }

    /**
     * Duplicate detection is per workspace, matching where the label lives. The same person recorded in
     * two workspaces being two records is the intended model, not a leak to be closed.
     */
    @Test
    void sameNumber_isAllowedInADifferentWorkspace() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        assertThat(service.register(workspaceB, ParticipantDescriptor.of("Ivan", PHONE)).getUniqueId())
                .isNotNull();
    }

    @Test
    void participantsWithoutPhones_doNotCollideWithEachOther() {
        service.register(workspaceA, ParticipantDescriptor.of("First", null));

        assertThat(service.register(workspaceA, ParticipantDescriptor.of("Second", null)).getUniqueId())
                .isNotNull();
    }

    // -------------------------------------------------------------------------------- updating

    @Test
    void update_replacesContactDataAndKeepsItsOwnNumber() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        var updated = service.update(
                created.getUniqueId(), created.getVersion(),
                ParticipantDescriptor.of("Ivan Petrenko", PHONE));

        assertThat(updated.getLabel()).isEqualTo("Ivan Petrenko");
        assertThat(service.revealContact(created.getUniqueId()).phone()).isEqualTo(PHONE);
    }

    @Test
    void update_refusesANumberAnotherParticipantInTheWorkspaceAlreadyHas() {
        var first = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));
        var second = service.register(workspaceA, ParticipantDescriptor.of("Olena", "+380501114455"));

        assertThatThrownBy(() -> service.update(
                second.getUniqueId(), second.getVersion(), ParticipantDescriptor.of("Olena", PHONE)))
                .isInstanceOf(DuplicateParticipantPhoneException.class);
        assertThat(service.revealContact(first.getUniqueId()).phone()).isEqualTo(PHONE);
    }

    @Test
    void update_withAStaleVersion_isRejected() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));
        service.update(created.getUniqueId(), created.getVersion(),
                ParticipantDescriptor.of("Ivan once", PHONE));

        assertThatThrownBy(() -> service.update(
                created.getUniqueId(), created.getVersion(),
                ParticipantDescriptor.of("Ivan twice", PHONE)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    /** The workspace is fixed at registration: there is no parameter that could move a participant. */
    @Test
    void update_cannotMoveAParticipantBetweenWorkspaces() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));

        service.update(created.getUniqueId(), created.getVersion(),
                ParticipantDescriptor.of("Ivan", PHONE));

        assertThat(service.browse(workspaceB, null, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    // ------------------------------------------------------------------------------- removal

    @Test
    void delete_removesOnlyThatParticipant() {
        var kept = service.register(workspaceA, ParticipantDescriptor.of("Kept", null));
        var removed = service.register(workspaceA, ParticipantDescriptor.of("Removed", null));

        service.delete(removed.getUniqueId());

        assertThat(service.browse(workspaceA, null, PageRequest.of(0, 10)).getContent())
                .extracting(WorkspaceParticipantModel::getUniqueId)
                .containsExactly(kept.getUniqueId());
    }

    /**
     * The contributor seam, end to end. Worth noting what would happen without it: the foreign key from
     * {@code rg_workspace_participant} to {@code rg_workspace} is deliberately restricting, so this
     * would fail on a constraint violation rather than silently orphan or silently mass-delete.
     */
    @Test
    void removingTheWorkspace_removesItsRoster() {
        service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));
        service.register(workspaceB, ParticipantDescriptor.of("Olena", "+380501114455"));

        workspaceService.delete(workspaceA);

        assertThat(participantRepository.countByWorkspaceUniqueId(workspaceA)).isZero();
        assertThat(participantRepository.countByWorkspaceUniqueId(workspaceB)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------ authority

    @Test
    void aStranger_isDeniedEveryOperation() {
        var created = service.register(workspaceA, ParticipantDescriptor.of("Ivan", PHONE));
        var version = created.getVersion();
        var participantId = created.getUniqueId();

        authenticate(STRANGER);

        assertThatThrownBy(() -> service.browse(workspaceA, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.browse(workspaceA, "ivan", PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.register(workspaceA, ParticipantDescriptor.of("Mallory", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(
                participantId, version, ParticipantDescriptor.of("Mallory", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revealContact(participantId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(participantId))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * An unauthenticated caller must be denied rather than reaching the repository. Guards deny on a
     * missing principal before any lookup, so this also confirms the guards are actually active — if
     * method security were inert, this would return an empty page instead of throwing.
     */
    @Test
    void anUnauthenticatedCaller_isDenied() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.browse(workspaceA, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(UniqueId user) {
        var principal = new AuthenticatedUserPrincipal(
                user, "Test User",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
