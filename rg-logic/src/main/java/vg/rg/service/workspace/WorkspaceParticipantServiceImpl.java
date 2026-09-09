package vg.rg.service.workspace;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.workspace.WorkspaceParticipantEntity;
import vg.rg.exception.workspace.DuplicateParticipantPhoneException;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.mapper.workspace.WorkspaceParticipantMapper;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.ParticipantPhone;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.repository.workspace.WorkspaceParticipantRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
class WorkspaceParticipantServiceImpl implements WorkspaceParticipantService {

    // Stable outcome codes, as in WorkspaceServiceImpl: the UI owns their translation, and neither
    // message ever carries the value that was rejected.
    static final String LABEL_REQUIRED = "workspace.participant.error.label-required";
    static final String LABEL_TOO_LONG = "workspace.participant.error.label-too-long";
    static final String SORT_NOT_SUPPORTED = "workspace.participant.error.sort-not-supported";

    private final UniqueIdService uniqueIdService;
    private final WorkspaceParticipantRepository repository;
    private final WorkspaceParticipantMapper mapper;
    private final WorkspaceProperties workspaceProperties;

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.WorkspaceParticipant.CREATE + "')")
    public WorkspaceParticipantModel register(UniqueId workspaceId, ParticipantDescriptor descriptor) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(descriptor, "descriptor");
        var sanitized = sanitize(descriptor);

        var limit = workspaceProperties.participantsMaxPerWorkspace();
        if (repository.countByWorkspaceUniqueId(workspaceId) >= limit) {
            throw new ParticipantLimitReachedException(limit);
        }
        rejectDuplicatePhone(workspaceId, sanitized.phone(), null);

        var entity = WorkspaceParticipantEntity.builder()
                // The scope comes from the operation, never from the caller's payload: that is what
                // makes a participant's workspace unforgeable from the UI.
                .workspaceUniqueId(workspaceId)
                .descriptor(sanitized)
                .build();
        return mapper.toModel(repository.saveWithNewUniqueId(entity, uniqueIdService));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#participantId, '"
            + LocalPermissions.WorkspaceParticipant.UPDATE + "')")
    public WorkspaceParticipantModel update(
            UniqueId participantId, int version, ParticipantDescriptor descriptor) {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(descriptor, "descriptor");
        var sanitized = sanitize(descriptor);

        var entity = repository.findById(participantId).orElseThrow(EntityNotFoundException::new);
        if (entity.getVersion() != version) {
            // Stale edit: the record advanced since the client loaded it (optimistic concurrency).
            throw new ObjectOptimisticLockingFailureException(
                    WorkspaceParticipantEntity.class, participantId);
        }
        rejectDuplicatePhone(entity.getWorkspaceUniqueId(), sanitized.phone(), participantId);

        // Contact data only. workspaceUniqueId is not updatable, so a participant cannot move; and
        // userUniqueId is set by invite redemption alone, never by an edit.
        entity.setDescriptor(sanitized);
        return mapper.toModel(repository.save(entity));
    }

    @Override
    @Transactional
    @PreAuthorize("@authorityChecker.hasAuthority(#participantId, '"
            + LocalPermissions.WorkspaceParticipant.DELETE + "')")
    public void delete(UniqueId participantId) {
        Objects.requireNonNull(participantId, "participantId");
        var entity = repository.findById(participantId).orElseThrow(EntityNotFoundException::new);
        repository.delete(entity);
    }

    /**
     * Label first, then identifier.
     *
     * <p>{@code CASE_INSENSITIVE_ORDER} rather than natural order, because natural order puts every
     * uppercase label ahead of every lowercase one — "Zoe" before "alice", which reads as broken. The
     * identifier then breaks precisely the ties that case-insensitivity creates.
     */
    private static final Comparator<WorkspaceParticipantModel> BY_LABEL_THEN_ID =
            Comparator.comparing(
                            WorkspaceParticipantModel::getLabel,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(
                            WorkspaceParticipantModel::getUniqueId,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#workspaceId, '"
            + LocalPermissions.WorkspaceParticipant.LIST + "')")
    public Page<WorkspaceParticipantModel> browse(
            UniqueId workspaceId, String labelFilter, Pageable pageable) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(pageable, "pageable");
        if (pageable.getSort().isSorted()) {
            // Refused rather than ignored: the ordering here is fixed, so a caller's sort cannot be
            // honoured, and discarding it silently would hand them data in an order they did not ask
            // for. A sortable column added later should fail here and then be implemented.
            throw new IllegalArgumentException(SORT_NOT_SUPPORTED);
        }
        var needle = labelFilter == null ? "" : labelFilter.trim().toLowerCase(Locale.ROOT);

        // Every descriptor in the workspace is opened, for the reason given on the interface: an
        // encrypted column can be neither searched nor ordered by the database. Bounded by the roster
        // limit, and the same cost whichever page was asked for.
        var ordered = repository.findByWorkspaceUniqueId(workspaceId).stream()
                .map(mapper::toModel)
                .filter(model -> needle.isEmpty() || matches(model.getLabel(), needle))
                .sorted(BY_LABEL_THEN_ID)
                .toList();

        // Sliced after ordering, which is the whole point: slicing first would return an arbitrary
        // subset rather than the alphabetically first one. The total is the filtered size, already
        // known for free from the scan above.
        var from = (int) Math.min(pageable.getOffset(), ordered.size());
        var to = Math.min(from + pageable.getPageSize(), ordered.size());
        return new PageImpl<>(ordered.subList(from, to), pageable, ordered.size());
    }

    private static boolean matches(String label, String needle) {
        return label != null && label.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority(#participantId, '"
            + LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT + "')")
    public ParticipantDescriptor revealContact(UniqueId participantId) {
        Objects.requireNonNull(participantId, "participantId");
        var entity = repository.findById(participantId).orElseThrow(EntityNotFoundException::new);
        // Nothing is logged here, deliberately, not even at debug: this is the one method whose return
        // value is a phone number in plaintext.
        return entity.getDescriptor();
    }

    /**
     * Validates and canonicalizes what the caller supplied, and stamps the current schema version so a
     * caller can neither choose nor forget it.
     */
    private ParticipantDescriptor sanitize(ParticipantDescriptor descriptor) {
        var label = descriptor.label() == null ? null : descriptor.label().trim();
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException(LABEL_REQUIRED);
        }
        if (label.length() > workspaceProperties.participantLabelMaxLength()) {
            throw new IllegalArgumentException(LABEL_TOO_LONG);
        }
        // Throws naming the field, and never echoing the value, if the number is implausible.
        var phone = ParticipantPhone.normalize(descriptor.phone());
        return ParticipantDescriptor.of(label, phone);
    }

    /**
     * Refuses a number this workspace already lists.
     *
     * <p>There is no index to consult and there cannot be one: the descriptor is encrypted with a fresh
     * IV per write, so two equal numbers produce different ciphertexts and the database can compare
     * nothing. So the roster is read and opened in memory. That is affordable precisely because
     * {@code rg.workspace.participants-max-per-workspace} bounds it, and it avoids importing the keyed
     * blind index that {@code specs/current/encryption.md} records as deliberately not copied here.
     *
     * <p>Comparing only within one workspace is also what keeps this from disclosing anything: the owner
     * can already read this roster in full.
     *
     * @param exclude the participant being updated, so it does not collide with itself
     */
    private void rejectDuplicatePhone(UniqueId workspaceId, String phone, UniqueId exclude) {
        if (phone == null) {
            return;
        }
        var clash = repository.findByWorkspaceUniqueId(workspaceId).stream()
                .filter(candidate -> exclude == null
                        || !Objects.equals(candidate.getUniqueId(), exclude.getLongValue()))
                .map(WorkspaceParticipantEntity::getDescriptor)
                .filter(Objects::nonNull)
                .anyMatch(candidate -> phone.equals(candidate.phone()));
        if (clash) {
            throw new DuplicateParticipantPhoneException();
        }
    }
}
