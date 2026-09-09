# Workspace participants

Current-state specification of participant registration as implemented.

Unlike most documents here there is no `specs/NNN-*/` feature directory behind it: the feature was
designed in conversation rather than through a feature spec, so this file is its only rationale record.

See also [workspace.md](./workspace.md) — a participant is a workspace's content, scoped by the active
workspace, exactly as a location is — and [encryption.md](./encryption.md), whose field-encryption
capability this is the first and only use of.

## Purpose

A workspace owner needs to register people who have not come through Telegram: a plumber, a client, a
neighbour. Two facts shape everything below.

- **A participant may never authenticate at all**, yet must still take part in workspace content —
  groups, events, later comments. So they cannot be modelled as "a user who has not arrived yet". They
  are a first-class record in this application with no identity subject behind them.
- The owner needs **a label they control** and **a phone number they can retrieve**. Both are personal
  data about a natural person — the only such data this application holds.

**A participant is a record, not a member.** Registering one grants no access to anything — not to the
workspace, not to its contents, not to the application. Membership is a separate, later concern; the
word "member" is deliberately left unused so it stays available for it. This is why adding the type
changed no access rule: `AuthorityChecker`, `WorkspaceScope` and `WorkspaceScopeResolver` are untouched.

## Why the data lives here

Two designs were considered first, and both fail.

- **Storing the label as an identity claim** collapses immediately: the identity service may have no row
  for someone who never authenticates, so there is nothing to hang a claim on.
- **A generic `POST /secure-data/` vault** in the identity service fails on its own terms — an untyped
  bag at a service boundary, a new abstraction layer needing its own justification, and this application
  would still have to fetch the data back on every render.

So this application owns the participant object, contact data included, **encrypted at rest**. That
required amending the constitution rather than contorting the design around it. Principle I carries a
narrow, exhaustive allowance for exactly this data under explicit controls (constitution 4.0.0).

**What encryption at rest does and does not buy** is stated in the amendment and repeated here because it
is easy to overclaim. It addresses disclosure to someone who can *read* the stored data — a dump, a
backup, a replica, direct database access. It does **not** address compromise of the application, which
holds the key, and it does not reach data already written to a backup when an erasure request is honored.

## Two identifiers, and the rule that keeps them cheap

| Identifier | Meaning |
|---|---|
| `unique_id` | Minted here. **The only identifier other content in this application ever references.** |
| `user_unique_id` | The identity subject, filled in if this person ever redeems an invite. **Never referenced by any content here.** Null for most participants, permanently for some. |

The identity-merge tax people expect from a two-identifier design only appears if content sometimes
points at the subject id — so it never does. A later binding therefore rewrites one column and no
references.

## Registering, and what registration deliberately does not do

**Registration never discloses whether the number already belongs to someone on the platform.** It
always creates a fresh participant and asks the identity service nothing at all. The alternative —
looking the number up to reuse an existing account — would hand every workspace owner an oracle for
testing arbitrary phone numbers against the platform's user base.

Binding a participant to a real account is therefore a *later* operation, and it happens elsewhere:
inside the identity service, on that person's own authenticated redemption of an invite. Whether
identity de-duplicates internally is its business and invisible here.

**That flow is not implemented**, and its prerequisite is recorded in
[open-decisions.md](./open-decisions.md).

## Capabilities

- **Register** a participant with a label and an optional phone number.
- **Browse** the active workspace's participants, ordered by label, filtered by label, a page at a time.
- **Reveal** one participant's number, as a separate action with its own permission.
- **Edit** a participant's contact data, with optimistic concurrency.
- **Remove** a participant, confirmed first and guarded against a double press.

Reading the roster and disclosing a number are separate operations, and the separation is *structural*:
`WorkspaceParticipantModel` has nowhere to put a phone number. It carries only whether one exists.

**A row shows a phone glyph, and only when there is a number — no digits, and no masked suffix.** A
suffix would still be information about a person, shown to anyone who can see the screen, in exchange
for nothing the glyph does not already convey. The glyph's *presence* is the entire message, which is
why absence is silence rather than a greyed-out icon or a "no phone" caption, and why it carries an
accessible name — a marker whose presence is the signal is invisible to a screen reader otherwise.

`WorkspaceParticipantModel` carries only whether a number exists — there is no partial form of it
anywhere in the layer. A surface that ever needs one should decide *how much may be shown* in `rg-logic`,
since that is a data-exposure rule rather than a formatting one, and not in the view that happens to
want it.

The edit form **pre-fills the number for a caller who holds `reveal-contact`**, and leaves the field
empty for one who does not — its helper text differs accordingly. Gating it on that permission is what
stops editing becoming a way around the reveal, and it means **opening the edit form is itself a
disclosure**: it calls `revealContact` exactly as the reveal action does, so if reveals are ever audited
this counts as one.

Pre-filling is not a convenience. Saving replaces the whole descriptor, so with an empty field an edit of
the label alone would silently wipe the number. A caller who cannot see the number therefore still faces
that, which is what their helper text warns about — and what a future non-owner editing role would need
to address properly.

### Ordering, filtering and paging

The roster is ordered by **label, then identifier**. The identifier tie-break is what makes the order
total: the label comparison is case-insensitive, so "ivan" and "Ivan" compare *equal*, and without it
their relative position would vary between calls and page boundaries would stop being repeatable. The
comparison is case-insensitive rather than natural because natural `String` order puts every uppercase
label ahead of every lowercase one — "Zoe" before "alice", which reads as broken.

**None of it can happen in SQL.** The label is encrypted with a fresh IV per write, so the database can
neither match it, index it, nor order by it. So **every call reads the workspace's rows and opens every
descriptor**, whatever page was asked for — paging through 1024 participants in pages of 50 is 21 full
scans, each a few milliseconds. The returned page is honest about its total but not about its cost. The
roster bound is what keeps that finite.

The total *is* accurate, and free: the filtered size is already known from the scan the ordering
required, which is exactly what a lazy data-binding count callback needs.

**One method serves both the filtered and unfiltered cases**, so they cannot disagree about order.

**The `Pageable` is interpreted, never delegated to Spring Data.** Only its offset and page size are
read. Handing it to a derived query would be a defect rather than a shortcut, because `label` names no
entity attribute and the query would fail on it at runtime. Two consequences follow: the repository has
deliberately **no `Pageable` overload** (a database page would be the wrong subset, not less work), and a
`Pageable` carrying a sort is **refused** rather than silently ignored — the ordering here is fixed, so a
caller adding a sortable column should fail loudly and implement it instead of receiving data in an order
it did not ask for.

**Duplicate numbers are refused within one workspace**, and allowed across workspaces — the same person
recorded in two workspaces is two records, which is correct rather than a compromise, because the label
is per workspace. Refusing discloses nothing: the comparison is against a roster the owner can already
read in full.

## The descriptor

`ParticipantDescriptor(schemaVersion, label, phone)` is serialized to JSON and encrypted into one opaque
column by `ParticipantDescriptorConverter`, following the `StringEncryptionConverter` pattern.

**What may go in it: contact attributes of the registered person, and nothing else.** The constitutional
allowance is exhaustive, so an added field is a governance change, not merely a schema one. Without that
rule stated, a blob becomes a dumping ground.

One record rather than a column per field, because encryption already destroys the only advantage
separate columns would have — neither form can be queried, sorted or constrained by the database, so
splitting would cost one envelope per field, one wiring site per field, and a migration per field added,
and buy nothing. It is an untyped bag only at the storage layer; in Java it is an explicit type.

Four properties of the format, each load-bearing:

- **`schemaVersion` gives the payload the discriminator the envelope lacks.** Lenient parsing already
  absorbs an *added* field; the version earns its keep when an existing field must be reinterpreted,
  which would otherwise mean rewriting every stored row.
- **Unknown fields are ignored**, so a rollback to an older build can still read rows a newer one wrote.
- **Nulls are omitted**, so a participant with no phone stores `{"v":1,"l":"…"}` rather than paying
  envelope space to say nothing.
- **`toString()` is redacted** on both the record and the entity. A record's generated `toString()`
  prints every component and the entity family carries Lombok's `@ToString`, so the default would put a
  label and a number into any log line that touched one.

### No row binding, and why that is the right call

Nothing in the descriptor ties it to the row it sits in, so a ciphertext relocated into another row
decrypts cleanly. That is deliberate.

Anyone able to relocate a blob has database *write* access, and their cheapest path is not the blob:
repointing `rg_workspace.owner_unique_id` at themselves gives them the whole workspace, after which the
application decrypts everything for them and any such check passes because blob and column agree. Every
ownership and scope column in this schema is plain writable data, so singling out the descriptor for a
consistency invariant buys nothing — and adds a second way for a *legitimate* read to hard-fail: a
partial restore, a row copied between environments, a future move-between-workspaces feature.

Proper AEAD row binding would need an `encode(String, byte[] aad)` overload and a format version byte in
`EncryptionService`, whose pinned-format test rightly makes that expensive. It is not planned.

## Access control

No new gate, and no change to the authority model. Every method guards with the established expression:

```java
@PreAuthorize("@authorityChecker.hasAuthority(#resourceId, '<localPermission>')")
```

The workspace for `create` and `list`, the participant's own identifier for `read`, `update`, `delete`
and `reveal-contact`. A workspace owner needs no participant capability inside their own workspace,
because owning the workspace already grants complete authority over its contents.

**The permission resource is `workspace-participant`, not `participant`**, breaking the shape `location`
set. These people are meant to take part in groups and events too, so a second participant-like type is
likely and the bare noun would then have two claimants. `location` has no such contested sibling and is
left as it is rather than renamed for symmetry.

`workspace-participant:reveal-contact` is the one non-CRUD capability. Like every local permission it is
*formal* today — ownership already grants it — but it names the boundary now, so granular non-owner
access later does not have to invent it, and it marks the one path that produces plaintext.

## Presentation

`WorkspaceParticipantsView` at `/workspaces/participants`, inside `WorkspaceLayout`, with a top-level
navigation entry shown only when `hasAuthority(activeWorkspaceId, 'workspace-participant:list')` holds —
the same arrangement the locations entry uses, and for the same reason: the workspace is the scope, not a
place to navigate through. The two entries are **independently** gated, and the active workspace is
resolved **once** per render, because resolving it provisions a default as a side effect.

**The screen is deliberately the same shape as the locations screen**: two tabs (browse with a filter,
add), a single-open accordion of rows, and management actions revealed inside the expanded panel rather
than sitting in the row.

Paging is an explicit **"Load more"** with a "showing X of Y" line, not infinite scroll and not a
`Grid`. Stating the total is the point: an alphabetical list that silently stops part-way looks like a
list of everyone, so a participant whose name sorts late would appear not to exist at all. Loading
appends rather than replaces, and the request always asks for everything loaded so far in one call —
ordering costs a full scan whichever page is wanted, so one call for *n* pages costs no more than one
call for the last of them, and the rendered list stays a single consistent snapshot.

A `Grid` with `setItemDetailsRenderer` would give infinite scroll and the accordion natively, and
`setItemsPageable` would bind straight to this service — the contract above is deliberately compatible
with it, so the swap needs no `rg-logic` change. It is not used yet for three reasons: `DisclosureList`
is shared with the locations screen and moving one screen alone would fork them apart again; `Grid` needs
a definite height, which means a scrolling region inside a scrolling page in a Telegram webview; and
`Grid` is a heavy lazily-chunked component while **[open-decisions.md](./open-decisions.md) item 1 — the
stale production bundle that stops lazily-chunked components loading at all — is still open.** Revisit
once that is closed, not before. That is not a resemblance maintained by discipline — both accordions are the
same `DisclosureList` component and therefore literally the same DOM and the same CSS, so neither screen
can be restyled without the other following. See [Shared presentation](#shared-presentation).

The revealed number appears in a **dialog**, not a notification: a notification auto-dismisses on a
timer, cannot be closed deliberately, and is the component most likely to be reused somewhere that logs
its text. Nothing on this path is logged, and the plaintext exists only for as long as the dialog does.

Mobile-first throughout: one column, a 44 px minimum row height, long labels wrapped rather than
clipped, wider layouts reached only through `min-width` queries. All text internationalized, Ukrainian
default with English second.

### Shared presentation

`DisclosureList` owns the accordion: the row DOM, the chevron, the header's accessibility attributes,
the single-open state, and `detailRow` for labelled values inside a panel.

**An expanded row stays expanded across a re-render.** Each entry carries a key — the domain identifier —
and the open entry's key survives the `reset()` that rebuilding performs, so the row reopens with its
updated content. That matters because saving an edit re-queries and rebuilds the whole list: without it
the panel the user was reading would collapse, and they would have to find and reopen the row to see
whether their change took. Keying on identity rather than position or label is what lets a renamed row
re-sort to a different place in the alphabet and still stay open. A row that is gone — deleted or
filtered out — simply does not reopen, and a row the user deliberately closed stays closed. Its styles are named for the
**pattern** — `disclosure-*` — rather than for either domain, so neither screen owns them and a third
can adopt them without a rename. `form-actions` and `stacked-form` are shared the same way.

**The class names are constants on `DisclosureList`, not string literals.** They are a contract spanning
the stylesheet, the component, both screens and both test classes, and a typo in any of them fails
*silently* — a rule that never matches, or an assertion that finds nothing and passes. Three of the
constants are not applied by the component at all (`ROW_ACTIONS`, `DETAIL_DESCRIPTION`, `DETAIL_META`):
they are conventions for panel *content* that callers apply, declared centrally so a caller never has to
guess a spelling. A class used inside one view stays a literal there — the rule is not that every class
becomes a constant, but that a name crossing a file boundary has exactly one definition.

Only two rules remain screen-specific: `.location-detail__maps` and `.participant-revealed-phone`.

Sharing one component rather than one convention is what makes the two screens genuinely identical:
duplicated DOM or duplicated CSS would let a fix to one silently miss the other.
`WorkspaceParticipantsViewTest` and `WorkspaceLocationsViewTest` therefore assert the *same* class
names, so a divergence between them means one screen was restyled alone. `DisclosureListTest` covers the
component's own contract — the header's accessibility attributes, the open-state bookkeeping across a
reset, and that every declared class name resolves to a stylesheet rule.

## Data

**`rg_workspace_participant`** — `unique_id`, `workspace_unique_id` (**NOT NULL and never updated**),
nullable `user_unique_id`, `descriptor` (`BLOB`), version token, audit columns. Index leading on
`workspace_unique_id`, then `created_at`, because the roster is paged in insertion order — an encrypted
column cannot be sorted in SQL.

**There is no plaintext label or phone column, and no blind index.** A reader of the schema must not be
able to see a person, and nothing here searches ciphertext — `encryption.md` records that the keyed blind
index `identity-service` uses was deliberately not copied, and this feature does not change that.

The size bound lives in configuration rather than the column type, so tightening or relaxing it is a
property change and not a migration. It is enforced **twice**: in the service, producing a localized
refusal, and again in the converter, which throws. A bound applied only at the edge is not a bound — the
converter also catches a caller reaching the repository directly.

### Removal, and why not `ON DELETE CASCADE`

The foreign key to `rg_workspace` is plain, with **no `onDelete`**, exactly like
`fk_rg_workspace_location_workspace`. Removal runs through `WorkspaceContentContributor` instead, and the
restrictiveness is load-bearing rather than incidental:

1. **It is what makes a forgotten contributor fail loudly.** Add a contained type without one and
   deleting a workspace violates a constraint — a test failure. With a cascade, the same omission deletes
   the rows silently, with no audit, no confirmation count and no opportunity to refuse.
2. **A cascade is invisible to JPA.** Loaded entities go stale, `@Version` is bypassed, caches are not
   invalidated.
3. **Auditing does not fire** for rows the database removed on its own.
4. **Removal has behavior**, not just referential consequences — repointing the active selection,
   re-provisioning a default, telling the user what is about to go.
5. **It generalizes past the database**, which is where a future contained type will unbind an invite or
   drop a stored file.

A cascade would genuinely add orphan protection when a workspace row is deleted *outside* the
application. If that is ever wanted, add it to every contained type at once and *behind* the
contributors, never instead of them.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `rg.workspace.participants-max-per-workspace` | 1024 | Roster size bound per workspace |
| `rg.workspace.participant-descriptor-max-bytes` | 2048 | Sealed envelope size bound |
| `rg.workspace.participant-label-max-length` | 128 | Label bound |

Bound by `WorkspaceProperties`; see [configuration.md](./configuration.md) for why the holder parses its
own values rather than letting the binder do it.

The roster bound is **the documented resource-control strategy** the constitution requires, and it is
what makes the index-free duplicate check defensible: registration opens every existing envelope in the
workspace, and the roster is sorted and filtered in memory, because the database can do neither.

## Where it lives

- **`rg-logic`**:
  - Access: `vg.rg.model.security.LocalPermissions.WorkspaceParticipant`,
    `vg.rg.service.workspace.WorkspaceParticipantScopeProvider`.
  - Services: `vg.rg.service.workspace.WorkspaceParticipantService`/`Impl`,
    `ParticipantWorkspaceContentContributor`.
  - Exceptions: `vg.rg.exception.workspace.ParticipantLimitReachedException` and
    `DuplicateParticipantPhoneException`.
  - Model: `vg.rg.model.workspace.ParticipantDescriptor`, `ParticipantPhone`, and
    `WorkspaceParticipantModel`.
  - Data: `vg.rg.entity.workspace.WorkspaceParticipantEntity` and `ParticipantDescriptorConverter`;
    `vg.rg.repository.workspace.WorkspaceParticipantRepository`; and
    `vg.rg.mapper.workspace.WorkspaceParticipantMapper`.
  - Schema: `rg-logic/src/main/resources/db/liquibase/004-workspace-participant.yaml`.
- **`rg-frontend-vaadin`**: `WorkspaceParticipantsView`, `DisclosureList` (shared with
  `WorkspaceLocationsView`), the participants navigation entry in `MainView`, and the `disclosure-*`
  rules in `META-INF/resources/styles.css` plus the one `participant-revealed-phone` rule.

`WorkspaceParticipantMapper` is **hand-written**, unlike the MapStruct mappers beside it. That mapping is
where it is decided what leaves the business layer about a natural person, and it has to read as such:
expressing "take the label but not the number, and derive a suffix from it" through MapStruct would mean
`expression = "java(...)"` strings the compiler does not check until code generation — the wrong place
for a rule whose failure mode is disclosing a phone number.
