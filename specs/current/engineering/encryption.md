# Field encryption at rest

## Purpose and integration

`EncryptionService` encrypts selected string values as binary data. The generic
`StringEncryptionConverter` opts a field in with
`@Convert(converter = StringEncryptionConverter.class)` and preserves null values in
both directions. Encrypted database columns use BLOB/VARBINARY storage.

The application usage is `rg_workspace_participant.descriptor`, through the typed
`ParticipantDescriptorConverter`. It serializes contact data to JSON and delegates
to the same encryption service. Contact exposure, permitted fields, and size bounds
belong to [participant logic](../logic/workspace/participants.md). Historical choices
are recorded in [domain history](../decisions/domain-history.md#contact-storage-and-encryption).

## Stored format

The envelope is `[keyId:1][iv:12][ciphertext+tag]`. Encryption is AES-256/GCM
(`AES/GCM/NoPadding`) with a random 12-byte IV per write and a 128-bit tag. Key
material is raw 32-byte data from configuration, without derivation; plaintext uses UTF-8.

The first byte selects a key in the range 0–255. There is no format-version byte and
no additional authenticated data (AAD); the key-id header is not supplied as AAD.
The IV is supplied to GCM as its nonce. Participant ciphertext is not bound to a row
or workspace through AAD.

Equal inputs produce independently randomized ciphertext, so encrypted labels/phones
cannot support plaintext equality constraints, joins, sorting, or searching in SQL.
The bounded participant scan is the application's duplicate/search mechanism.
Decryption selects the stamped key and rejects missing keys, malformed values, or
authentication failure rather than interpreting them as plaintext.

`EncryptionServiceTest.decode_whenGivenPinnedFormatVector_returnsExpectedPlaintext`
pins this layout against an independently computed vector.

## Configuration

```properties
rg.encryption.current-key-id=0
rg.encryption.keys.0=<base64 of 32 random bytes>
```

Generate production key material with `openssl rand -base64 32` and supply it through
the configured environment secret, such as `RG_ENCRYPTION_KEY_0`. Local/test profile
fixtures are not production keys; actual key material is not included here.

`EncryptionProperties` is a configuration record with no safe default key.
Construction rejects an absent keyring/current key, key identifiers outside 0–255,
a current identifier absent from the ring, invalid Base64, and keys not exactly
32 bytes. The validated runtime keyring is immutable. There is no encryption-disabled
mode; contexts scanning this component require usable configuration.

[Configuration holders](configuration.md) describe registration and binding.

## Rotation

Adding a key and selecting it as current changes new writes. Existing envelopes
continue decrypting with their stamped identifiers. There is no re-encryption job:
keys referenced by stored data must remain available until that data is rewritten.
Rotation provides read compatibility, not automatic re-keying or erasure of backups.

## Persistence verification

Stateful JPA converters are obtained through Spring's managed-bean registry when
Hibernate encounters an annotated entity. Context startup alone does not exercise
that resolution.

`ParticipantDescriptorPersistenceFuncTest` persists, flushes, clears, and reloads,
then checks raw JDBC bytes so a plaintext round trip cannot masquerade as encryption.
`EncryptionWiringFuncTest` exercises the generic string converter, which is not
attached to a production entity. Converter tests cover nulls, randomized writes,
tampering, omitted null JSON fields, unknown fields, rotation, and the sealed-byte cap.

## Evidence

- [EncryptionService.java](../../../rg-logic/src/main/java/vg/rg/service/EncryptionService.java)
- [EncryptionProperties.java](../../../rg-logic/src/main/java/vg/rg/config/EncryptionProperties.java)
- [StringEncryptionConverter.java](../../../rg-logic/src/main/java/vg/rg/entity/StringEncryptionConverter.java)
- [EncryptionServiceTest.java](../../../rg-logic/src/test/java/vg/rg/service/EncryptionServiceTest.java)
- [EncryptionWiringFuncTest.java](../../../rg-logic/src/test/java/vg/rg/entity/EncryptionWiringFuncTest.java)
- [ParticipantDescriptorPersistenceFuncTest.java](../../../rg-logic/src/test/java/vg/rg/entity/workspace/ParticipantDescriptorPersistenceFuncTest.java)
