<!--
Sync Impact Report
- Version change: 3.1.0 -> 4.0.0
- Modified principles:
  - I. Personal-Data Prohibition and Permitted Business Data (added a narrow, exhaustive allowance
    for encrypted workspace-participant contact attributes, and an explicit statement of what
    encryption at rest does not protect against)
- Added sections: none
- Modified sections:
  - Architecture and Data Constraints (the personal-data service remains authoritative for identity
    mapping; the blanket "MUST NOT persist personal data" bullet now carries the Principle I
    exception)
  - Development Workflow and Mandatory Test Coverage (the review confirmation is no longer "no
    personal data is persisted", which would be false, but that persisted personal data is confined
    to what Principle I permits and carries its required controls)
- Removed sections: none
- Amendment rationale: a workspace owner must be able to register participants -- a plumber, a
  client, a neighbour -- who have not authenticated through Telegram and may never do so, yet must
  still take part in workspace content. Such a person has no record in the identity service, so
  neither an identity claim nor an abstract user ID can carry the owner's label for them or a phone
  number to reach them. The data therefore has to live in this application or the capability cannot
  exist. Rather than contort the design around the prohibition, the prohibition is narrowed by an
  exhaustive, controlled allowance.
- Compatibility impact: MAJOR. This redefines a governance guarantee that reviews, specifications,
  and the security posture relied on: the application was previously guaranteed to persist no
  personal data about natural persons at all. It now MAY persist a bounded, encrypted contact set
  for workspace participants only. Nothing already implemented becomes non-compliant, and no other
  category of personal data is permitted.
- Migration needs: none for existing data -- the allowance is additive and no personal data is
  persisted today. The first use MUST land with its encryption, masking, bounding, and
  no-leak coverage in the same change; `specs/current/` MUST record the allowance's controls.
- Approval: approved by the project maintainer on 2026-09-04, as part of the workspace-participants
  plan review.
- Follow-up TODOs: none.
-->
# RG Telegram Bot Constitution

## Core Principles

### I. Personal-Data Prohibition and Permitted Business Data
The application MUST NOT collect, persist, derive, or expose personal data about natural persons,
including emails, phone numbers, addresses, Telegram identity data, or equivalent identifiers,
except for the two narrowly scoped allowances below—a display name, and a workspace participant's
contact attributes. This prohibition applies to databases, client storage, logs, metrics, traces,
analytics, error reports, and support artifacts. The application MAY hold and process company data
and payment data when each payment record is bound only to an abstract user ID supplied by the
designated secure service; it MUST NOT store or obtain the mapping from that ID to a person. This
boundary permits the business domain while keeping personal data under the secure service's
control.

The application MAY temporarily retain a display name supplied by the designated secure service
only in server-side authenticated session state or a bounded server-side cache and only for the
current user's experience. The application MUST NOT derive the name from Telegram data, use it as
an identity or authorization input, or write it to durable storage, client storage, logs, metrics,
traces, analytics, errors, or support artifacts. The retained value MUST be removed when the user
logs out, the session expires, authentication is replaced, or the cache entry is evicted, whichever
occurs first. Cache entries MUST have a finite lifetime no longer than the associated authenticated
session.

The application MAY persist a bounded set of contact attributes for a **workspace participant**—a
person whom a workspace owner registers, who has not authenticated and may never do so—and MAY
obtain them for authorized display, only when all of the following hold: the attributes are limited
to a display label chosen by the registering owner and a single contact phone number; they are
encrypted at rest through the project's field-encryption capability, under keys supplied by
approved runtime configuration and never present in source control; they are masked by default in
every interface, with plaintext disclosed only by a separate, deliberate, authorized action; they
are never written to logs, metrics, traces, analytics, client storage, error reports, or support
artifacts; they are never used as an identity, authorization, ownership, or audit input; their
count and serialized size are bounded by configuration; and they are erasable on request. This
allowance is exhaustive: it extends to no other personal data and to no other subject, it does not
permit resolving a participant to a natural person outside the application's own records, and it
does not weaken Principle II's delegation of authentication and Telegram handling to the designated
secure service, which remains the sole authority for identity.

Encryption at rest under this allowance addresses disclosure to a reader of the stored data—a
database dump, a backup, a replica, or direct database access. It MUST NOT be presented as
protection against compromise of the application itself, which holds the keys, and it does not
reach data already written to a backup when an erasure request is honored. Residual risk MUST be
recorded explicitly rather than implied away.

The sole Telegram-data exception is request-scoped transport of a size-bounded raw Telegram
`initData` value directly from the authentication callback to the designated secure-service facade.
Outside the secure boundary, code MUST NOT parse, inspect, derive identity from, retain, display,
persist, log, copy into session state, or otherwise reuse that value. The value MUST be released as
soon as the facade call completes and MUST NOT appear in an app-facing result or principal.

### II. Secure-Service Trust Boundaries
Personal-data handling, Telegram authorization, and Telegram verification MUST be delegated to the
designated secure service. This application MUST use only that service's authenticated, versioned
contract and its opaque abstract user IDs; it MUST NOT implement an alternate identity verification
flow or accept client assertions as proof of identity. A display name MAY cross this boundary only
as authenticated secure-service output and only under Principle I's lifetime and usage constraints.
Secrets and credentials MUST come from approved runtime configuration and MUST NOT appear in source
control, logs, URLs, or client-visible errors. All external input MUST be authenticated where
applicable, validated, and safely encoded before use. Unsolicited inbound data—including requests,
callbacks, pushed events, messages, and uploads—MUST have an explicit enforceable size or item-count
bound before unbounded parsing, allocation, or retention. Data intentionally requested from an
external service MUST instead use a documented resource-control strategy appropriate to the
integration, such as pagination or cursors, bounded page sizes, projection, streaming, batching,
backpressure, cancellation, or a justified hard limit. Requested responses MUST NOT be required to
have a fixed total-size ceiling when the selected strategy keeps application resource use
controlled. These rules ensure that identity and personal-data responsibilities have a single,
auditable owner without rejecting legitimate requested datasets solely because of total size.

Forwarding bounded raw Telegram `initData` into the facade is transport to that boundary, not proof
of identity. Only the secure-service implementation may authenticate, parse, or derive identity from
it; every consumer MUST treat the value as opaque and untrusted until the facade returns an
authenticated authorization result.

### III. Delegated Telegram Authorization and Identity
The secure service MUST perform Telegram authorization and verification before it provides this
application with an abstract user ID or authorization result. Protected operations MUST authorize
the supplied abstract ID for the requested action and MUST never rely solely on client-provided
user, Telegram, or resource identifiers. Authentication and authorization failures MUST deny
access safely without exposing internal details. The application MUST retain only the opaque
reference required to complete protected operations and MAY additionally retain the authenticated
display name under Principle I; it MUST NOT replicate any other Telegram profile data or Telegram
sessions. The display name MUST NOT affect authentication, authorization, ownership, or audit
identity. The authentication callback MAY transiently carry bounded raw Telegram `initData` solely
into the secure facade under Principle I; it MUST NOT establish application authentication from
that value or retain it after redemption.

### IV. Resilient Middleware Boundaries
The application MUST remain a thin presentation and orchestration layer around explicit,
versioned external-service contracts. Calls MUST use finite connection and operation timeouts,
bounded retries with backoff only for safe operations, and idempotency controls where duplicate
execution can cause harm. Timeout values MUST be explicit, configurable where environments differ,
and tested for safe failure behavior. Partial failure MUST produce a clear, recoverable user state;
it MUST NOT silently discard updates, fabricate success, or leave ambiguous operations. Contract
tests MUST cover success, validation failure, authorization failure, timeout, unavailability, and
incompatible responses. Operational health MUST be observable without collecting personal data.

Performance requirements MUST measure application-owned processing separately from time spent
waiting for external systems. A flow that includes communication with an external service outside
the project's operational control MUST NOT be governed by an end-to-end percentile or absolute
user-latency acceptance target, such as requiring a percentage of users to finish within a fixed
number of seconds. Such a flow MUST instead define and verify finite external-call timeouts, safe
timeout behavior, and any relevant performance budget for application-owned work. An end-to-end
latency objective MAY be adopted only when the project controls the complete path or has an explicit,
enforceable dependency latency contract.

### V. Mobile-First, Accessible, Internationalized User Experience
All user flows MUST be designed first for narrow Telegram webview and mobile screens, then
progressively enhanced for wider displays. Interfaces MUST provide clear status, validation,
loading, empty, error, and retry states and MUST prevent accidental duplicate or destructive
actions. Core tasks MUST be keyboard accessible, use semantic controls, preserve readable
contrast and touch-target sizes, and avoid relying on color alone. User-facing text MUST be
concise, actionable, and must not reveal internal or personal data. Critical flows MUST be tested
at representative narrow and wide viewport sizes.

All user-facing and assistive text—including navigation, page titles, controls, validation,
authorization states, errors, retry guidance, accessibility labels, and notifications—MUST be
resolved through the application's internationalization mechanism rather than hardcoded in UI
code. Supported locales MUST be declared explicitly, use complete resource bundles, and provide a
deterministic default-locale fallback for missing or unsupported locale input. Messages with
variable content MUST use named or indexed placeholders and locale-aware pluralization instead of
string concatenation. Dates, times, numbers, and monetary values MUST use locale-aware formatting;
business currency and timezone semantics MUST remain explicit and MUST NOT be inferred from locale.
Domain and service layers MUST expose stable, non-localized outcome codes or message keys so that
presentation code owns translation without moving business rules into the UI. Critical flows MUST
be tested in the default locale and at least one declared non-default locale, including long labels,
missing-key fallback, and locale-aware formatting. These rules keep every supported experience
usable and equivalent without coupling business behavior to a language.

### VI. Module Ownership
The `rg-logic` module MUST own business logic, domain rules, and business validation. The
`rg-frontend-vaadin` module MUST own UI presentation, routing, layout, and user interaction; it
MUST NOT contain business rules. The UI module MUST invoke business behavior through explicit
`rg-logic` interfaces, and `rg-logic` MUST NOT depend on UI code. This separation keeps business
behavior reusable, independently testable, and free of presentation concerns.

## Architecture and Data Constraints

- The external personal-data service is the sole authoritative store for personal data about
  authenticated users, and for every mapping between an abstract user ID and a natural person.
- This application MAY persist and process company data, payment data bound to secure-service
  abstract user IDs, technical configuration, opaque external references, and non-personal
  operational state when their purpose and lifetime are documented.
- The application MUST NOT persist or attempt to resolve personal data, including the association
  between an abstract user ID and a natural person. Two allowances under Principle I are exhaustive
  exceptions: it MAY process and temporarily retain the secure-service-provided display name, and it
  MAY persist the encrypted contact attributes of a workspace participant subject to that
  principle's controls.
- Temporary sensitive values MUST remain in memory for the shortest practical time and MUST NOT
  be written to durable storage, client storage, telemetry, or diagnostic artifacts.
- Telegram authorization and verification are secure-service responsibilities. The application may
  transport bounded raw Telegram `initData` directly into that boundary under Principle I and
  otherwise consumes only authenticated results.
- Public endpoints and external-service integrations MUST have explicit schemas, validation,
  authentication, authorization, timeout, error, compatibility, and direction-appropriate
  resource-control behavior. Unsolicited inbound data requires an enforceable hard bound; requested
  data requires controlled retrieval or processing but not necessarily a fixed total-size ceiling.
- Dependencies MUST be actively maintained, version-pinned through the project's dependency
  management, and reviewed for security and licensing risk before adoption.
- Simplicity is mandatory: new persistence, asynchronous infrastructure, or abstraction layers
  require a demonstrated user or reliability need and a documented tradeoff.

## Specification Artifacts and Organization

Active specifications MUST live under the `specs/current/` directory, which is the single
authoritative home for the project's current-state specification set. Content that is superseded
MUST be updated or removed rather than left stale; `specs/current/` MUST describe intended behavior
as it currently stands.

Specifications MUST be decomposed into focused Markdown files. Each feature MAY be captured in its
own Markdown file, and each logical area (domain) MUST be represented as a separate Markdown file so
that ownership and scope stay clear. Files MUST use self-explaining names that identify the feature
or domain they describe.

Specification files MAY reference one another through relative Markdown links to connect related
material instead of duplicating it. A defined inter-file order is not required, and specifications
SHOULD NOT assume one unless a real need exists. If such a need does arise, the order SHOULD be made
explicit rather than implied; a zero-padded numeric filename prefix (for example, `0001-...`,
`0002-...`) is one suggested way to express it, and any other clear, explicit scheme is acceptable.

## Development Workflow and Mandatory Test Coverage

Every change MUST state its user outcome, affected trust boundaries, and testable acceptance
criteria before implementation. All executable application code MUST be covered by unit and
integration tests. Every production-code change MUST add or update both unit and integration tests
that cover its business behavior and relevant failure paths. Contract or integration tests are
mandatory when an external-service or Telegram boundary changes. Security-sensitive changes
require a threat-focused review, including authentication, authorization, input validation, data
exposure, and abuse cases.

Acceptance criteria for flows that call external services MUST separate application-owned
performance from dependency wait time. They MUST verify configured finite timeouts and safe timeout
handling, and MUST NOT impose end-to-end latency percentages or fixed completion deadlines unless
the project controls the full path or an enforceable dependency latency contract is in scope.

Changes MUST pass formatting, static analysis, relevant unit and integration tests, and dependency
checks before merge. Reviews MUST explicitly confirm that any persisted personal data is
confined to Principle I's allowances and carries their required controls—encryption at rest,
masking by default, configured bounds, and erasability—that no personal data is leaked through
logs or telemetry, and that any retained display name obeys its session/cache lifetime.
Breaking contract changes require a migration and rollback plan.
Deployments MUST support health checks, actionable non-personal telemetry, rollback, and staged
verification proportional to risk. Unresolved violations MUST block release unless Governance
grants a documented, time-bounded exception with an owner and remediation date.

Every task plan (`tasks.md`) MUST end with a final actualization phase whose purpose is to update
the current specification set in `specs/current/` so that it reflects the change just implemented. A
change is not complete until its corresponding specifications have been actualized: affected feature
and domain files MUST be revised, cross-references and ordering prefixes kept consistent, and any
superseded content removed. Reviews MUST confirm that this actualization was performed.

## Governance

This constitution is the highest authority for project engineering decisions. Specifications,
plans, tasks, code reviews, and release decisions MUST demonstrate compliance. Amendments require
a written proposal describing the rationale, compatibility impact, migration needs, and affected
principles; approval by the project maintainers; and an update to this document before the change
is adopted.

Constitution versions follow semantic versioning: MAJOR for incompatible removal or redefinition
of governance guarantees, MINOR for a new principle or materially expanded obligation, and PATCH
for non-semantic clarification. Every amendment MUST update the version, amendment date, and Sync
Impact Report. Compliance MUST be reviewed during feature planning and pull-request review, and
must be audited before each production release. Exceptions MUST be explicit, risk-assessed,
approved by a maintainer, assigned to an owner, and expire on a recorded date.

**Version**: 4.0.0 | **Ratified**: 2026-08-08 | **Last Amended**: 2026-09-04
