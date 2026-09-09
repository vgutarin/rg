package vg.rg.service.workspace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vg.rg.exception.workspace.DuplicateParticipantPhoneException;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.unique.id.model.UniqueId;

/**
 * Registering and managing the people a workspace owner records who have not authenticated.
 *
 * <p>Every method takes the workspace or a participant identifier, so no operation can be expressed
 * without a scope. Guards pass the identifier of the thing being acted upon: the workspace for
 * {@link #register} and {@link #browse}, the participant's own identifier for everything else.
 *
 * <p><strong>Registration never discloses whether the number already belongs to someone on the
 * platform.</strong> It always creates a fresh participant and asks the identity service nothing. The
 * alternative — looking the number up to reuse an existing account — would hand every workspace owner an
 * oracle for testing arbitrary phone numbers against the platform's user base. Binding a participant to a
 * real account happens later and elsewhere: inside identity, on that person's own authenticated
 * redemption of an invite. Whether identity de-duplicates internally is its business and invisible here.
 *
 * <p>Reading the roster and disclosing a contact number are separate operations with separate
 * permissions, and {@link WorkspaceParticipantModel} has nowhere to put a phone number, so the split
 * cannot be bypassed by accident.
 */
public interface WorkspaceParticipantService {

    /**
     * Records a new participant in the given workspace.
     *
     * @throws ParticipantLimitReachedException     if the workspace is already at its roster bound
     * @throws DuplicateParticipantPhoneException   if this workspace already lists that number
     * @throws IllegalArgumentException             if the label is missing or either field is out of
     *                                              bounds. Carries a stable message key — never the
     *                                              value that was rejected.
     */
    WorkspaceParticipantModel register(UniqueId workspaceId, ParticipantDescriptor descriptor);

    /**
     * Replaces a participant's contact data.
     *
     * @param version the version the caller last read, for optimistic concurrency
     */
    WorkspaceParticipantModel update(UniqueId participantId, int version, ParticipantDescriptor descriptor);

    void delete(UniqueId participantId);

    /**
     * One page of the workspace's participants, ordered by <strong>label and then identifier</strong>,
     * optionally filtered by label.
     *
     * <p>The identifier tie-break is what makes the order total. Two participants may share a label, and
     * the label comparison is case-insensitive, so {@code "ivan"} and {@code "Ivan"} compare
     * <em>equal</em> — without it their relative position would vary between calls and page boundaries
     * would stop being repeatable.
     *
     * <p><strong>Ordered and filtered in memory, not in SQL, and it cannot be otherwise.</strong> The
     * label lives inside an encrypted column with a fresh IV per write, so the database can neither
     * compare it, index it, nor order by it — see {@code specs/current/encryption.md}. So
     * <strong>every call reads and opens the whole roster</strong>, whatever page is asked for: the
     * returned page is honest about its total but not about its cost. That is affordable only because
     * {@code rg.workspace.participants-max-per-workspace} bounds how many envelopes there can be.
     *
     * <p>The total carried by the returned {@link Page} is <em>accurate</em>, and costs nothing extra —
     * the filtered size is already known from the scan the ordering requires. That is exactly what a
     * lazy data-binding count callback needs.
     *
     * <p><strong>The {@code Pageable} is interpreted here, never handed to Spring Data.</strong> Only
     * its offset and page size are used. Delegating it would be a bug rather than a shortcut, because
     * {@code label} names no entity attribute and a derived query would fail on it at runtime.
     *
     * @param labelFilter case-insensitive substring the label must contain; blank or {@code null}
     *                    returns the whole roster
     * @throws IllegalArgumentException if the {@code Pageable} carries a sort. The ordering here is
     *                                  fixed, so an unhonourable sort is refused rather than silently
     *                                  ignored: a caller adding a sortable column should fail loudly
     *                                  rather than receive data in an order it did not ask for.
     */
    Page<WorkspaceParticipantModel> browse(UniqueId workspaceId, String labelFilter, Pageable pageable);

    /**
     * Discloses one participant's contact data in plaintext. The one path that produces it, which is why
     * it carries its own permission.
     *
     * <p>Callers MUST NOT log the result, put it in a notification, or hold it beyond the render that
     * needed it.
     */
    ParticipantDescriptor revealContact(UniqueId participantId);
}
