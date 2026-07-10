# Phase 0 Research: Secure Permission-Aware Telegram App

## Decision 1: Contract and implementation placement

**Decision**: Keep the facade and its models in `rg-logic` under `vg.rg.security` and
`vg.rg.security.model`, with the development implementation isolated in `vg.rg.security.dev`. The
frontend depends only on `rg-logic` and invokes the bean through the facade.

**Rationale**: No independent consumer justifies a separate API module or versioned Java package.
The published `rg-logic` artifact is the semantically versioned application-facing facade artifact.
Responsibility-focused packages retain the seam while removing redundant Gradle configuration and
dependency edges.

**Alternatives considered**: Keep a separate API module (rejected: no independent consumer yet); keep the verifier
in `rg-frontend-vaadin` (rejected: leaks Telegram identity and business/security logic into
presentation); flatten every security class into one package (rejected: obscures models and facade
implementations); expose the development implementation directly (rejected: prevents extraction).

## Decision 2: Telegram data handoff

**Decision**: The Vaadin authentication callback accepts a bounded raw Telegram `initData` value and
immediately forwards it to `SecureAuthorizationFacade.redeemAuthorizationGrant` as a
`TelegramInitDataRequest`. Frontend code does not parse, retain, display, persist, or log the value.
Only the facade implementation validates Telegram identity fields and returns an app-safe
authenticated principal.

**Rationale**: This removes the extra one-time-grant protocol while retaining one explicit security
boundary for verification. The sensitive value exists outside the facade only as request-scoped
transport data for the shortest practical time and never becomes application identity or session
state.

**Governance and compatibility**: A project maintainer approved the narrowly bounded transport rule
on 2026-08-11. The constitution now permits only this direct opaque handoff while prohibiting all
other handling outside the secure boundary. The app-facing response and principal remain unchanged
and opaque. Migration removes frontend parsers/profile models and replaces them with a single
bounded forwarding bridge; no persisted-data migration is required.

**Alternatives considered**: Use a separate exchange and redemption operation (rejected by the
requested simpler contract); return an authorization result from browser JavaScript (rejected:
creates a client assertion); continue parsing Telegram profile data in the frontend (rejected:
violates module and privacy boundaries).

## Decision 3: Facade operations and result model

**Decision**: Define only `redeemAuthorizationGrant(TelegramInitDataRequest)`. Use an explicit
request/result pair and a closed outcome set:
`AUTHORIZED`, `DENIED`, `INVALID_REQUEST`, `EXPIRED`, `UNAVAILABLE`, and `INCOMPATIBLE`.

**Rationale**: The operation performs Telegram verification and creates app-safe, bounded session
state in one boundary call. Protected routes and operations subsequently check that session state
inside the application. Closed outcomes produce deterministic UI states and transport-neutral
contract tests.

**Alternatives considered**: Three exchange/redeem/authorize methods (rejected: unnecessary grant
lifecycle for the development architecture); one `authenticate` method returning Spring
`Authentication` from the facade (rejected: framework-coupled); a second remote capability-check
operation (rejected: unnecessary contract and latency for the chosen bounded-session model);
exception-only failures (rejected: unstable and hard to map safely); arbitrary
unvalidated permission strings (rejected: unknown values are too easy to grant accidentally).

## Decision 4: App principal and permission enforcement

**Decision**: Store the identity service's `sub`, `name`, and `consentGiven` fields unchanged and its
validated, deduplicated permission set in the authenticated app principal, together with the RG-owned
`authenticationFlow`.
The external contract uses case-sensitive lowercase
`resource:action` identifiers (`home:view`, `reports:view`, `request:submit`). An immutable,
resource-grouped `Permissions` registry is the application allowlist; it validates declaration
syntax and uniqueness at startup. Map only recognized values to Spring authorities, filter
navigation for usability, and use a session-aware `AuthorityChecker` for direct route entry and
Spring `@PreAuthorize` enforcement on protected service methods. The checker validates the current
typed principal, requires non-null `sub`, then checks one recognized permission. The
principal retains syntactically valid unknown permission values for contract fidelity, but unknown
values received in a principal and unknown required permissions grant nothing. Every protected
capability requires `sub`; consent is preserved but is not a capability input.

**Rationale**: Hidden navigation is not authorization. Combining route checks with method security
denies bookmarked routes and direct service invocation without adding another remote facade method
or retaining Telegram data.

**Alternatives considered**: `APP_HOME_VIEW`-style identifiers (rejected: expose Java-enum naming in
the external API and mix application/resource/action concerns); automatic enum-name conversion
(rejected: refactoring could silently change the contract); rely on hidden menu items (rejected:
bypassable); reauthorize remotely for every operation (rejected: extra coupling and latency; the
authenticated session already defines authorization lifetime); call security logic directly from each view
(rejected: duplicates business rules in UI).

## Decision 5: Session lifetime, resilience, and idempotency

**Decision**: The authenticated HTTP/Vaadin session defines authorization lifetime. The application
`AuthorityChecker` rejects missing, malformed, or unsupported authentication before protected access,
but a null `sub` or false consent flag is not malformed by itself. A null-sub principal receives no
protected authority or content; false consent does not suppress permissions when `sub` is present.
Potentially harmful actions require a
caller-generated idempotency key, and the application service layer atomically binds it to the
string `sub` and records the safe outcome. Subject-dependent operations return a controlled denial,
create no idempotency record, and execute no effect when `sub` is absent. Ambiguous mutations are not
retried automatically.

The development facade deliberately applies no replay guard, rate limiter, or separate permission
policy. It verifies each Telegram request and grants the complete recognized development permission
catalog. The real identity service remains responsible for production abuse controls and permission
policy.

**Rationale**: This satisfies fail-closed recovery and prevents duplicate effects from repeated taps
or delayed responses without introducing a message broker. Stable request IDs support non-personal
diagnostics. Production permission changes become visible when the user authenticates again or the
session is refreshed; they are not fetched again during each business call.

**Alternatives considered**: Optimistic access during outage (rejected: violates fail-closed rule);
unbounded retry (rejected: overload and ambiguous outcomes); client-only button disabling
(rejected: does not protect against retries or concurrent requests); a separate authorization
decision TTL (rejected: duplicates the session lifecycle and can invalidate an otherwise active
session unexpectedly).

## Decision 6: Development identity strategy

**Decision**: The development secure implementation validates Telegram data, rejects bot accounts,
uses the verified Telegram numeric ID's decimal representation temporarily as string `sub`, grants
the complete development permission catalog, sets `consentGiven=true` and
`authenticationFlow=TELEGRAM`, and immediately discards parsed Telegram fields. It does not invent a
display name. The bot token is mandatory outside explicit test configuration.

**Rationale**: Production authentication receives its opaque string subject and identity fields
from identity-api. The development implementation needs only a deterministic subject for local
sessions and idempotency; converting the verified numeric ID to a string keeps the contract shape
without introducing a second identifier type or a development-only mapping store.

**Alternatives considered**: Allocate an app `UniqueId` (rejected: duplicates the identity-api
subject contract); maintain a local persistent identity mapping (rejected: unnecessary development
state); expose additional Telegram profile fields (rejected: they are not part of the identity-api
principal contract).

## Decision 7: Vaadin development model and shell

**Decision**: Continue with Java Flow and Aura on Vaadin 25.2. Use `AppLayout` with primary drawer,
`DrawerToggle`, a scrollable `SideNav`, and permission-filtered `SideNavItem`s. Use built-in
component semantics and supported Aura/base tokens.

**Rationale**: The project is already a Flow application. Vaadin 25.2 documentation recommends the
drawer when small-screen support is important because it collapses to an overlay, and SideNav is
the intended primary navigation component. This avoids a React/Hilla dependency and works with the
existing Spring Security setup.

**Alternatives considered**: React views (rejected: no user need and requires additional starter);
bottom tabs (rejected for varying permission sets and future navigation growth); custom navigation
HTML (rejected: weaker semantics and unnecessary maintenance).

## Decision 8: Visual system and responsive composition

**Decision**: Use a calm sky accent (`#0084d1` light / `#38bdf8` dark), paired neutral backgrounds,
high contrast, Aura base size 20, base font size 15, radius 4, restrained layered cards, filled
current SideNav item, and neutral non-primary buttons. Base CSS targets a 320px viewport; wider card
grids and header actions appear only through `min-width` queries.

**Rationale**: Trust and clarity matter more than data density. Aura tokens preserve dark mode and
focus behavior, while spacious density improves Telegram webview touch use. The visual identity is
distinct without relying on user avatars or personal data.

**Alternatives considered**: Preserve the current generic blue/default composition (rejected: weak
state hierarchy); Lumo utilities (rejected: active theme is Aura); dark-only styling (rejected:
system light/dark support is already configured).

## Decision 9: Explicit user experience states

**Decision**: Render one protected-state container with mutually exclusive `LOADING`, `PERMITTED`,
`NO_ACCESS`, `DENIED`, `TEMPORARILY_UNAVAILABLE`, `INCOMPATIBLE`, and `RETRYING` states. Replace
protected content before a recheck; announce state changes in a polite live region; focus the state
heading after terminal failures; expose a single retry action only where safe.

**Rationale**: One state machine prevents stale content from remaining visible and makes error
mapping testable. Concise, semantic content supports keyboard and assistive technology users.

**Alternatives considered**: Toast-only failures (rejected: transient and inaccessible for primary
state); exceptions routed to a generic error page (rejected: poor recovery); overlaying errors while
old content remains (rejected: protected data leakage).

## Decision 10: Verification approach

**Decision**: Use existing JUnit/Spring test dependencies. Move verifier unit tests to `rg-logic`,
add a reusable common facade contract plus adapter-specific outcome suites, integration-test initial authorization/capability checks and
Spring session mapping, and validate responsive/keyboard scenarios through a documented manual
browser matrix until an approved browser-test dependency exists.

**Rationale**: This meets current planning needs without silently introducing an external
dependency. Contract tests are the current adapter-equivalence gate; manual responsive checks cover browser behavior
that component unit tests cannot prove.

**Alternatives considered**: Add TestBench or Playwright immediately (not selected because the
repository has neither and new dependencies require explicit approval); rely only on manual tests
(rejected for security and contract behavior).

## Decision 11: Development credential configuration

**Decision**: Allow developers to place real bot credentials in ignored
`application-local.properties`, `application-local.yml`, or `application-local.yaml` files and load
them only with the explicit Spring `local` profile. Commit only safe property names/placeholders;
use synthetic credentials in tests and approved runtime secret injection outside local development.
Add repository ignore rules for all three local filenames. Never print their values or copy them
into artifacts. Rotate any credential that has been committed, shared, logged, or otherwise exposed.

**Rationale**: Local configuration provides a practical development workflow without treating a
developer's secret file as source-controlled application configuration. Ignore rules reduce
accidental commits, while profile isolation and production secret injection keep environments
separate.

**Alternatives considered**: Prohibit local property credentials entirely (rejected: unnecessarily
burdens Telegram Mini App development); permit credentials in any untracked filename (rejected:
inconsistent and difficult to audit); commit encrypted or plaintext development credentials
(rejected: expands access and lifecycle risk).

## Decision 12: Locale ownership, fallback, and refresh

**Decision**: Treat locale as frontend presentation state. Support exactly `uk-UA` and `en`, make
Ukrainian the canonical base bundle and deterministic default, and store a manual choice only in
`VaadinSession`. A new session and any null or unsupported locale start in Ukrainian regardless of
browser, JVM, or Telegram locale. Missing English keys fall back to Ukrainian; a total key miss
renders a localized Ukrainian safety message rather than a raw key or blank. Changing locale
rerenders attached components in place through Vaadin locale propagation while retaining route, Spring
authentication, typed principal, and permissions, and does not call the secure
facade solely because of the switch.

**Rationale**: This implements FR-022 through FR-024 deterministically and keeps language choice
separate from identity and authorization. Vaadin 25.2 propagates locale changes through its locale
API and `LocaleChangeObserver`/locale signals, while session-scoped locale supports navigation and
reload without persistent client tracking. A Ukrainian base bundle makes framework fallback match
the product default.

**Alternatives considered**: Browser or Telegram language detection (rejected: contradicts the
clarified Ukrainian-first session policy and introduces identity-derived behavior); persistent
cookie/local-storage selection (rejected: session-only was chosen); English base fallback
(rejected: contradicts deterministic Ukrainian fallback); manually mutating only the picker and
current labels (rejected: misses page titles, validation, accessibility text, and nested views).

## Decision 13: Defer external HTTP extraction

**Decision**: Do not ship or maintain an RG REST service/client contract in this feature. Keep the
facade outcomes transport-neutral and use a normative facade contract document. Actual independently
hosted extraction, migration, rollback rehearsal, and transport-specific errors belong to a separate
future feature.

**Rationale**: The current runtime already delegates to identity-api through its owned client. A
speculative RG HTTP contract had drifted from the Java and identity contracts and created unused
acceptance surface.

**Alternatives considered**: Keep dormant REST adapters or a speculative OpenAPI draft (rejected:
unused and already inconsistent); move HTTP types into `rg-logic` (rejected: transport coupling);
delete the facade seam (rejected: removes the stable application boundary).

## Decision 14: Identity principal contract and representation

**Decision**: `AuthenticatedUserPrincipal` mirrors identity-api's serializable principal fields as
nullable `String sub`, nullable `String name`, `Set<String> permissions`, and boolean
`consentGiven`, and adds the RG-owned `AuthenticationFlow authenticationFlow`. Null `sub` and false
consent are structurally valid. The identity adapter normalizes and validates upstream permissions
before principal construction. Application authorization requires both non-null `sub` and a
recognized permission; idempotency ownership uses that same opaque subject.

**Rationale**: Mirroring the source contract removes lossy conversion and avoids coupling the
authentication boundary to the application's `UniqueId` domain type. The explicit flow records how
the identity was established without deriving it from subject syntax.

**Alternatives considered**: Parse `sub` as `UniqueId` (rejected: the identity contract defines an
opaque string and parsing can reject valid subjects); keep only subject and permissions (rejected:
loses identity-api fields); infer Telegram flow from the request or subject (rejected: implicit and
brittle).

## Decision 15: Facade implementation selection

**Decision**: Select `DevSecureAuthorizationFacade` only when
`rg.secure-service.enabled=true`. Select `IdentitySecureAuthorizationFacade` when the property is
false or absent. Exactly one facade must exist. Identity-rest-client auto-configuration may remain
active in development mode; if it requires an API key, local configuration supplies a
clearly fake, non-secret value paired with a loopback/non-production base URL. The selected
development facade never invokes that client.

**Rationale**: Development behavior must be explicit, while the production-oriented identity path
is the default. Creating an unused identity client is acceptable locally when its placeholder
credential is unmistakably fake and cannot authenticate against a real service.

**Alternatives considered**: Select by API-key presence (rejected: secrets should not define mode);
load both and choose at runtime (rejected: ambiguous authority); require identity credentials in
development mode (rejected: a fake non-secret placeholder is sufficient).

## Decision 16: Artifact versioning and compatibility

**Decision**: Version the published `rg-logic` facade and identity-api/rest-client artifacts
semantically and pin exact versions. Requests and responses contain no `contractVersion`. Compile
compatibility plus common and adapter-specific contract tests provide current compatibility proof.
Breaking changes increment major and require migration/rollback plans; compatible additions increment
minor and fixes increment patch. Snapshot versions are development-only.

**Rationale**: Artifact versioning matches the Java boundary without duplicating version state in
every DTO. The consumed dependency, not a speculative HTTP document, is the compatibility authority.

**Alternatives considered**: Restore DTO version fields (rejected: excessive and previously removed);
defer all versioning to extraction (rejected: conflicts with governance); version Java packages
(rejected: unnecessary package churn).

## Decision 17: Provisional principal and subject-required denial

**Decision**: Treat an otherwise valid principal with null `sub` and/or false consent as valid session
state. Null `sub` suppresses all protected authorities and capabilities regardless of returned
permissions; non-empty permissions with null `sub` produce one privacy-safe value-free warning.
False consent does not suppress permissions when `sub` is non-null. Every protected operation returns
a stable denial before creating state or invoking effects when `sub` is absent.

**Rationale**: This mirrors identity-api without inventing identity or consent. Controlled denial
keeps the business boundary fail-closed and prevents null subjects from causing internal errors or
unowned operations.

**Alternatives considered**: Reject the principal entirely (rejected: session establishment remains
valid); allow permission-only access (rejected by clarification and constitution); execute with null
ownership (rejected: violates authorization and idempotency guarantees).

## Decision 18: Identity transport resilience ownership

**Decision**: Finite connect/read/request timeouts and the pre-parse raw-response limit belong to
identity-rest-client, which owns its internally built HTTP client. RG consumes a published client
supporting those controls plus duplicate-permission visibility, targets two-second connect and
eight-second overall deadlines, defaults the configurable response limit to 256 KiB, performs no
automatic retry, and maps timeout/client failure to `UNAVAILABLE` and oversized/malformed output to
`INCOMPATIBLE`.

**Rationale**: RG cannot reliably retrofit timeouts onto a client that constructs its own
`RestClient`. Transport ownership provides one testable configuration contract and satisfies the
constitution's finite-timeout and external-input-bound requirements. Duplicate occurrences cannot be
warned about after a client has silently converted the response to a set, so the transport contract
must preserve occurrences or expose an equivalent duplicate indicator.

**Alternatives considered**: Wrap the call in an executor (rejected: does not reliably cancel I/O);
accept library defaults (rejected: not explicit or bounded); automatically retry authentication
(rejected: unnecessary traffic and ambiguous safety).

## Decision 19: Configurable input-limit lifecycle

**Decision**: Read one immutable startup snapshot for the Telegram payload limit (default 32 KiB),
raw identity response limit (default 256 KiB), identity unique-permission count (default 1024), and
identity permission length (default 128 characters). Values must be positive and well formed; absent
values use defaults, invalid values fail startup, changes require restart, and no application hard
ceilings are imposed. The additive property contract is
`rg.secure-service.max-init-data-size`, `vg.identity.rest-client.max-response-size`,
`rg.secure-service.identity.max-permission-count`, and
`rg.secure-service.identity.max-permission-length`, respectively. The two data-size defaults are
expressed as Spring `DataSize` values `32KB` and `256KB`; the former measures UTF-8 bytes and the
latter measures raw response bytes.

**Rationale**: Startup validation prevents silent security-policy drift while configuration permits
environment-specific bounds. An immutable snapshot keeps in-flight requests deterministic. Optional
properties with defaults preserve existing deployments, and property-specific errors can remain
actionable without echoing untrusted configured values.

**Alternatives considered**: Hard-coded limits (rejected by clarification); dynamic refresh (rejected:
changes policy mid-request); fallback from invalid values (rejected: hides misconfiguration); hard
application ceilings (rejected by clarification).

## Decision 20: Identity-only permission normalization

**Decision**: Identity-api permission count and length limits apply only to identity results. Duplicate
occurrences are reduced to unique values with exactly one privacy-safe warning, and the configured
count is evaluated after deduplication. The development facade uses its fixed internal catalog and is
not constrained by identity-response settings.

**Rationale**: The identity adapter owns validation of untrusted external output, while the development
catalog is trusted startup-owned data. Warning on duplicates exposes upstream drift without denying an
otherwise usable result. The separate raw-response byte bound protects the pre-normalization input.

**Alternatives considered**: Apply identity limits to both adapters (rejected by clarification); reject
duplicates (rejected); silently deduplicate (rejected: hides upstream drift); count raw occurrences
(rejected by clarification).

## Decision 21: Deterministic primary-action usability

**Decision**: For every permission set with an allowed primary action, require that action to be
visible, clearly labeled, keyboard-focusable, and startable from the landing experience within two
user activations. Click, tap, Enter, or Space activation counts; passive page load, focus movement,
and scrolling do not.

**Rationale**: This produces repeatable component/browser acceptance evidence without an undefined
participant study while preserving a direct, understandable primary path.

**Alternatives considered**: Require a ten-person study (rejected for this development feature);
remove the outcome (rejected: weakens the primary path); count passive events as interactions
(rejected: makes the metric ambiguous).
