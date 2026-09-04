# Field encryption at rest

Current-state specification of field-level encryption as implemented. Unlike the other documents here
there is no `specs/NNN-*/` feature directory behind it: the capability was copied from the
`identity-service` project rather than designed here, and this file is its only rationale record.

The two projects hold **independent copies with independent keys**, deliberately, rather than sharing a
library. Nothing decrypts the other's data, so each only needs to be self-consistent. Revisit that if a
third consumer appears, or if data ever has to move between the two.

## Purpose

Store selected string columns as ciphertext so a database dump does not disclose their contents, while
leaving the column nullable and the surrounding code unchanged. A field opts in with one annotation:

```java
@Convert(converter = StringEncryptionConverter.class)
private String someSensitiveField;
```

The column becomes binary (`BLOB`/`VARBINARY`), and `StringEncryptionConverter` encrypts on write and
decrypts on read.

## The stored format

`EncryptionService` produces `[keyId:1][iv:12][ciphertext+tag]`, with AES-256/GCM
(`AES/GCM/NoPadding`), a random 12-byte IV per value, and a 128-bit tag. Key material is the raw 32
bytes from configuration, used with no derivation.

**Stamping the key id is what makes rotation possible**: a value names the key that produced it, so it
stays readable after that key stops being current.

Two consequences of the format worth stating, because neither is visible from the annotation:

- **The same input encrypts to a different value every time** (the IV is fresh per write). So an
  encrypted column cannot carry a unique constraint, be joined on, or be searched — not by equality,
  prefix, or range. A column needing lookups needs a different mechanism; `identity-service` uses a
  keyed blind-index hash alongside the ciphertext, and that was **deliberately not copied here** since
  nothing in this project searches an encrypted column. The participant roster does need to detect a
  duplicate phone number, and does it by opening every envelope in one workspace under an explicit count
  bound rather than by importing an index — see
  [workspace-participants.md](./workspace-participants.md#configuration).
- **The key id and IV are outside GCM's authenticated data, and there is no version byte.** Changing the
  layout later therefore needs an explicit data migration rather than a discriminator.

`EncryptionServiceTest.decode_whenGivenPinnedFormatVector_returnsExpectedPlaintext` pins the layout
against a vector computed independently from this description, so a change that would orphan already
stored values fails a test rather than being discovered in production.

## Configuration

```properties
rg.encryption.current-key-id=0
rg.encryption.keys.0=<base64 of 32 random bytes>
```

Generate a key with `openssl rand -base64 32`. Production takes it from the environment as
`RG_ENCRYPTION_KEY_0`; the `local` and `test` profiles commit Base64 of readable ASCII, so those values
cannot be mistaken for real secrets.

`EncryptionProperties` follows the shared holder convention — see
[configuration.md](./configuration.md). It is a record, unlike most of them, because it binds a map and
has nothing to hand-parse, which also makes the bound key material immutable after startup.

What sets it apart is that it declares **no defaults**. A search radius has a safe fallback; a missing
encryption key does not, so `EncryptionService` rejects an incomplete configuration at construction
time.

**Startup fails if the keyring is absent, malformed, or not 32 bytes**, and this is by design: there is no
"encryption disabled" mode, because the failure mode of one would be silently storing plaintext. Since
`EncryptionService` is a `@Component`, that requirement applies to every Spring context scanning
`vg.rg`, functional tests included.

## Rotation, and what it does not do

Add a key and point `current-key-id` at it. New writes use it; old values keep decrypting through their
stamped key id.

**There is no re-encryption job.** A row keeps the key it was written with until something rewrites it,
so a retired key can never be removed from the keyring — it only stops being used for writes. Rotation
is read-compatibility, not re-keying. Actually retiring a key means rewriting every row that used it,
which nothing here does today.

## Current usage

**One column: `rg_workspace_participant.descriptor`.** See
[workspace-participants.md](./workspace-participants.md).

It does not use `StringEncryptionConverter` directly. A participant's contact data is a small record
serialized to JSON, so it has its own `AttributeConverter<ParticipantDescriptor, byte[]>` —
`ParticipantDescriptorConverter` — which follows the same `@Component @Converter` shape and calls the
same `EncryptionService`. The two converters differ only in what they serialize.

That use is what finally exercises **the Hibernate resolution path**, which no test could reach while no
entity carried the annotation: a stateful converter has to be obtained from Spring's managed-bean
registry rather than instantiated reflectively, and if Hibernate cannot do that it fails at runtime, not
at compile time. `ParticipantDescriptorPersistenceFuncTest` covers it with a persist/flush/clear/reload,
plus a raw JDBC read asserting the column really holds ciphertext — a round trip alone would pass just as
happily if the converter stored plaintext.

`EncryptionWiringFuncTest` remains the only coverage `StringEncryptionConverter` itself has, since no
entity carries that one.

Two things the participant use adds on top of this capability, both documented where they live rather
than here: a **size bound** on the sealed envelope (configuration, enforced in the service and again in
the converter), and a **schema version inside the payload**, which is how it compensates for the envelope
carrying no version byte.
