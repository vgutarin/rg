# Workspace participants

## Responsibility and language

A participant is a person record created by a workspace owner, independently of
Telegram authentication. Registration grants no application/workspace access and
performs no identity-service lookup. It does not disclose whether a phone belongs
to an identity-service user.

The local `unique_id` is the identifier referenced by other application content.
Nullable `user_unique_id` is an identity-subject field; registration leaves it unset
and contact update does not change it.

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

## Roster query and consistency

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

## Configuration

| Property | Default |
| --- | --- |
| `rg.workspace.participants-max-per-workspace` | 1024 |
| `rg.workspace.participant-descriptor-max-bytes` | 2048 |
| `rg.workspace.participant-label-max-length` | 128 |

The workspace/created-at database index supports storage access, not alphabetical
ordering of encrypted labels. [Registration logic](registrations.md) references the
participant's local identity and resolves labels at read time.

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
