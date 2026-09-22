# Workspace participants

## Responsibility and language

A participant is a person record created by a workspace owner, independently of
Telegram authentication. An unbound participant grants no application/workspace
access. A participant with `user_unique_id` is a member of its workspace for event
visibility policy matching, but does not gain workspace navigation or management.
Registration performs no identity-service lookup and does not disclose whether a
phone belongs to an identity-service user.

The local `unique_id` is the identifier referenced by other application content.
Nullable `user_unique_id` is an identity-subject field. Registration leaves it unset
and contact update does not change it. Membership-request approval is the only
binding path. The database permits one bound participant per workspace/user while
allowing many unbound records.

## Model and disclosure

`ParticipantDescriptor` is a contact value with schema version, owner-selected label,
and optional phone. Schema version 1 serializes as `v`, `l`, and optional `p`.
Unknown JSON fields are ignored and null values omitted.

The contact label and phone are the participant personal-data allowance. They are
encrypted together in `rg_workspace_participant.descriptor`, a BLOB with no plaintext
label/phone columns or blind index. Descriptor/entity string representations redact
contact contents. The row also has required immutable workspace identity, version,
nullable identity subject, and audit fields.

`WorkspaceParticipantModel` exposes label and `phoneRecorded`, not a phone or masked
suffix. The mapper is the data-exposure boundary. `revealContact` separately returns
the descriptor under the participant-addressed reveal-contact guard; it logs no
plaintext contact. UI details are in [participants](../../ui/participants.md).

## Commands, validation, and outcomes

- Register sanitizes the descriptor, checks the workspace's roster count and duplicate
  normalized phone, creates a fresh identifier, and returns the roster model.
- Update takes participant identifier, read version, and a complete replacement
  descriptor. Stale versions fail; an absent phone replaces the stored phone with
  absence. Workspace and identity-subject fields are not edited.
- Labels are trimmed, required, and bounded by configuration.
- `ParticipantPhone.normalize` accepts an optional leading + and 5–15 digits;
  spaces, nonbreaking spaces, hyphens, dots, and parentheses are removed. No region
  is inferred. Blank input means no phone; invalid input returns a localization key
  without echoing the number. UI input allows 32 characters; normalized length is at
  most 16. It uses Java's digit classification.
- Duplicate non-null normalized numbers are refused within a workspace, excluding
  the edited row; the same number in another workspace is independent.
- Delete removes the participant's event registrations before deleting the person row.
- Count and duplicate refusals use `ParticipantLimitReachedException` and
  `DuplicateParticipantPhoneException`; label/sort/phone validation uses stable keys.

Create/list address the workspace; update/delete/reveal-contact address the participant.
The local resource name is `workspace-participant`. Ownership grants these operations
under the [shared access policy](README.md#authorization-scope).

## Functional requirements

- **FR-001**: Registration MUST create an unbound participant in the supplied workspace after label/phone sanitization and roster-limit and duplicate-phone checks.
- **FR-002**: Update MUST reject a stale version and replace the complete contact descriptor, including removal of an omitted phone.
- **FR-003**: Roster browsing MUST apply eligibility, label filtering, and fixed label/identifier ordering before slicing the page; a supplied sort MUST be refused.
- **FR-004**: Plaintext contact retrieval MUST use the participant-addressed reveal-contact guard.
- **FR-005**: Participant deletion MUST remove that participant's event registrations before removing the participant.
- **FR-006**: Join requests MUST use the authenticated subject and an audience-visible event, and MUST be a no-op for an existing member or pending requester.
- **FR-007**: Owner approval MUST explicitly create a bound participant or bind an eligible owner-selected unbound participant; contact matching MUST NOT select a target automatically.
- **FR-008**: Approval and rejection MUST remove the resolved request so a later request can be submitted.

## Invariants

- **INV-001**: The roster model carries no phone number or masked suffix (checked by `WorkspaceParticipantServiceFuncTest`).
- **INV-002**: The participant schema stores contact data in a binary descriptor without plaintext label/phone columns (checked by `WorkspaceParticipantSchemaTest` and the raw-column assertions in `ParticipantDescriptorPersistenceFuncTest`).
- **INV-003**: At most one participant is bound to a given workspace/user pair (checked by schema constraint `uq_rg_workspace_participant_workspace_user`; null user bindings remain repeatable).
- **INV-004**: At most one pending request exists for a workspace/user pair (checked by schema constraint `uq_rg_workspace_membership_request_pending_user` and `WorkspaceMembershipRequestPersistenceFuncTest`).

## Consistency

### Roster queries

Browse decrypts all descriptors in the workspace, filters by trimmed case-insensitive
label containment, orders by case-insensitive label then identifier, and only then
slices by pageable offset/size. Its total is the filtered count. A sorted pageable is
rejected; its ordering is fixed. Paging does not reduce the full-workspace scan.

Register/update/delete are service transactions. Duplicate-phone and count checks are
application reads, not database uniqueness constraints on encrypted values. The
workspace count bound limits normal roster work; it is not a claim of serialized
concurrent registration.

The descriptor converter enforces the sealed-byte limit when writing. It also protects
direct repository writes. The service sanitizes label/phone but does not perform a
separate encrypted-size check.

Encryption protects read access to stored dumps/backups, not a compromised application
holding keys, arbitrary database writers, or data already captured in backups.
Technical envelope/key behavior is owned by [encryption](../../engineering/encryption.md).

### Membership requests

An authenticated viewer requests to join from a visible event. The request belongs to
the event workspace; its nullable event reference records the origin and never creates
an event registration. It snapshots the authenticated account label in an encrypted
`ParticipantDescriptor` without a phone. Submission locks that workspace before checking
for an existing request. `pendingUserUniqueId` is the authenticated identity for that
check; the database permits exactly one request per workspace/user. An existing member
or requester is an idempotent no-op. Approval or rejection deletes the request and logs
the decision with the request, workspace, and deciding-user identifiers, permitting a
later request.

Only the workspace owner reads pending requests or decides them. Approval explicitly
chooses either an owner-selected unbound participant or creation from the encrypted requester descriptor;
when that name is absent or too long, the owner supplies the required label. Both paths
trim the label, and creating a participant enforces the configured roster limit before
approving the request. No name, phone, or contact-based automatic match exists. The
encrypted requester label is disclosed only in the owner’s request queue. Rejection is
authorized against the request's workspace, rather than treating the workspace identifier
as a participant resource.

The membership-approval candidate query applies the unbound predicate before its
case-insensitive label/identifier ordering and pageable slice. It shares the roster query's
workspace-list authorization and encrypted-label scan, so each page contains only eligible
participants.

## Configuration

| Property | Default |
| --- | --- |
| `rg.workspace.participants-max-per-workspace` | 1024 |
| `rg.workspace.participant-descriptor-max-bytes` | 2048 |
| `rg.workspace.participant-label-max-length` | 128 |

The workspace/created-at database index supports storage access, not alphabetical
ordering of encrypted labels. [Registration logic](registrations.md) references the
participant's local identity and resolves labels at read time.

## Success criteria

- **SC-001**: Filtered roster pages continue in case-insensitive label/identifier order and report the complete filtered total (`WorkspaceParticipantServiceFuncTest`).
- **SC-002**: A persisted descriptor round-trips to its original contact data while the raw database column contains ciphertext (`ParticipantDescriptorPersistenceFuncTest`).
- **SC-003**: Approval using an eligible request label creates a bound participant with the trimmed label; rejection releases the pending-request slot (`WorkspaceMembershipRequestPersistenceFuncTest`).

## Related contracts

- [Workspace authorization](README.md#authorization-scope).
- [Participant UI](../../ui/participants.md) and [event registrations](registrations.md).
- [Encryption mechanism](../../engineering/encryption.md).

## Evidence

- [WorkspaceParticipantServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceParticipantServiceImpl.java)
- [WorkspaceParticipantMapper.java](../../../../rg-logic/src/main/java/vg/rg/mapper/workspace/WorkspaceParticipantMapper.java)
- [ParticipantDescriptor.java](../../../../rg-logic/src/main/java/vg/rg/model/workspace/ParticipantDescriptor.java)
- [ParticipantPhone.java](../../../../rg-logic/src/main/java/vg/rg/model/workspace/ParticipantPhone.java)
- [ParticipantDescriptorConverter.java](../../../../rg-logic/src/main/java/vg/rg/entity/workspace/ParticipantDescriptorConverter.java)
- [004-workspace-participant.yaml](../../../../rg-logic/src/main/resources/db/liquibase/004-workspace-participant.yaml)
- [WorkspaceParticipantServiceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceParticipantServiceFuncTest.java)
- [WorkspaceParticipantLimitFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceParticipantLimitFuncTest.java)
- [ParticipantPhoneTest.java](../../../../rg-logic/src/test/java/vg/rg/model/workspace/ParticipantPhoneTest.java)
- [ParticipantDescriptorConverterTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/ParticipantDescriptorConverterTest.java)
- [ParticipantDescriptorPersistenceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/ParticipantDescriptorPersistenceFuncTest.java)
- [WorkspaceParticipantServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceParticipantServiceImplTest.java)
- [WorkspaceParticipantSchemaTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceParticipantSchemaTest.java)
- [007-event-visibility-membership.yaml](../../../../rg-logic/src/main/resources/db/liquibase/007-event-visibility-membership.yaml)
- [WorkspaceMembershipRequestServiceImpl.java](../../../../rg-logic/src/main/java/vg/rg/service/workspace/WorkspaceMembershipRequestServiceImpl.java)
- [WorkspaceMembershipRequestServiceImplTest.java](../../../../rg-logic/src/test/java/vg/rg/service/workspace/WorkspaceMembershipRequestServiceImplTest.java)
- [WorkspaceMembershipRequestPersistenceFuncTest.java](../../../../rg-logic/src/test/java/vg/rg/entity/workspace/WorkspaceMembershipRequestPersistenceFuncTest.java)
