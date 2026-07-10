# Feature Specification: Secure Permission-Aware Telegram App

**Feature Branch**: `not-created`

**Created**: 2026-08-11

**Status**: Draft

**Input**: User description: "Dev preparation. Create a facade for a secure service that holds personal data and provides authorization. Temporarily keep the secure-service implementation in this project, use the facade as the basis for its future external API, and build a simple Telegram app with UI customized by permissions from the secure service. Move the implementation to the external service after the required structures are ready."

## Clarifications

### Session 2026-08-11

- Q: Which locales must the initial release fully support? → A: Ukrainian default, English supported.
- Q: How should the app select and remember the user’s locale? → A: Ukrainian initially; manual choice remembered for the current session.
- Q: What should happen immediately after the user changes the language? → A: Refresh the current view immediately while preserving authentication and permissions.

### Session 2026-08-26

- Q: How should the application bound the raw identity-api response before duplicate permissions are removed? → A: Enforce a configurable total response limit with a 256 KiB default, reject oversized responses before parsing, and apply no application-enforced hard ceiling.
- Q: Should the configured identity-api permission-count limit be checked before or after duplicate permissions are removed? → A: Check the unique permission count after duplicate removal.
- Q: If identity-api returns the same permission more than once, should the application reject the result or silently remove duplicates? → A: Continue with unique permission values and emit one privacy-safe warning for the result without logging permission contents.
- Q: Should the configurable permission-count and permission-length limits apply to authorization results from both facade implementations or only identity-api? → A: Apply them only to identity-api results; the development implementation uses its fixed internal permission catalog.
- Q: Should changes to the configured payload and permission limits require an application restart, or take effect while the application is running? → A: Read and validate the limits at startup; changes require an application restart and never affect in-flight requests.
- Q: What should happen when a configured payload, permission-count, or permission-length limit is zero, negative, or malformed? → A: Fail application startup with a non-sensitive configuration error.
- Q: Should the configurable permission limits default to 1,024 entries and 128 characters per permission, with no application-enforced hard ceilings? → A: Yes; the unique-count and length limits are positive configurable values with those defaults and no application-enforced hard ceilings.
- Q: What maximum size should the raw Telegram authentication payload have before the application rejects it? → A: Use a configurable positive limit with a 32 KiB default and no application-enforced hard maximum.
- Q: What is the maximum number of permissions an authorization result may contain before it is rejected as incompatible? → A: For identity-api results, default the configurable unique-permission maximum to 1,024 and reject any normalized result that exceeds it as incompatible.
- Q: Should the application automatically retry a failed authorization-service request, or wait for the user to retry? → A: Never retry automatically; show a safe failure state and allow an explicit user retry only after the prior attempt has definitively completed.
- Q: How should the requirement that 95% of authorized users reach the landing experience within three seconds be tested? → A: Verify configured authorization timeouts only; do not measure end-to-end landing time.
- Q: When `rg.secure-service.enabled` is absent, should the application use the identity-service implementation or fail startup? → A: Missing property selects the identity-service implementation; startup fails only if zero or multiple facade implementations are active.
- Q: How should the application handle an identity-api result with a null `sub` and non-empty permissions? → A: Establish the session if the principal is otherwise valid, but ignore all permissions and deny every protected capability; emit a privacy-safe warning that does not include identity, payload, or permission values.
- Q: When the secure service authorizes a principal whose `sub` is null or whose `consentGiven` is false, how should the application treat that principal? → A: Establish the session if the principal is otherwise valid; null `sub` denies every protected capability regardless of permissions, while false consent is retained and does not suppress permissions when `sub` is non-null.
- Q: Should this feature include the actual secure-service extraction and migration rehearsal, or only prepare the facade and identity-service adapter for later extraction? → A: This feature ends with the facade, development implementation, and identity-service adapter; actual extraction, migration, rollback rehearsal, and equivalence testing move to a separate future feature.
- Q: When the identity service changes a user’s permissions during an active application session, when must the application apply the new permissions? → A: Apply changed permissions only after reauthentication or authenticated-session refresh; the current principal remains authoritative until then.
- Q: How should the current facade contract be versioned and checked for compatibility without adding `contractVersion` fields to requests or responses? → A: Version the facade and identity-api as build artifacts using semantic versioning; breaking contract changes require a major version, migration plan, rollback plan, and adapter contract tests.
- Q: How should the “first-time users start an allowed primary action within 30 seconds” success criterion be validated? → A: Replace the participant percentage with a deterministic check that an allowed primary action is visible, clearly labeled, keyboard-focusable, and startable within two user interactions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter the App Securely (Priority: P1)

As a Telegram user, I want the app to recognize whether I am authorized through the designated secure service so that I can enter without creating another identity or exposing personal data to the app.

**Why this priority**: Every useful interaction depends on a trusted authorization result, and the application must not create a second identity or personal-data store.

**Independent Test**: Provide authorized, unauthorized, expired, and invalid secure-service results and verify that only an authorized structurally valid principal establishes a session; false consent alone does not invalidate an authorized principal, while a null `sub` grants no protected access even when permissions are returned.

**Acceptance Scenarios**:

1. **Given** the secure service has verified and authorized the Telegram user, **When** the user opens the app, **Then** the app opens the permitted landing experience without asking for personal details.
2. **Given** the user cannot be verified or is not authorized, **When** the user opens the app, **Then** access is denied with a concise, non-sensitive explanation and no protected content is shown.
3. **Given** an authorization result is expired, malformed, or cannot be authenticated, **When** the app evaluates it, **Then** the result is rejected and the user receives a safe retry path.
4. **Given** the secure service authorizes a structurally valid principal with a null `sub`, **When** the app establishes authentication, **Then** it preserves the supplied result for the session but ignores all returned permissions, exposes no protected capability, and emits a privacy-safe warning if permissions were non-empty.
5. **Given** the secure service authorizes a structurally valid principal with a non-null `sub` and a false consent flag, **When** the app establishes authentication, **Then** it preserves false consent and honors recognized permissions without treating consent as an authorization prerequisite.

---

### User Story 2 - Preserve the External-Service Boundary (Priority: P2)

As a service owner, I want the development and identity-service implementations isolated behind the same application facade so that consumers remain independent of implementation and transport details and a later extraction can be delivered separately.

**Why this priority**: A stable separation prevents personal-data responsibilities from leaking into the Telegram app and makes a later extraction a bounded follow-up rather than a consumer rewrite.

**Independent Test**: Run the shared application-facing authorization contract against the development and identity-service facade implementations, verify configuration selects exactly one implementation, and confirm that Telegram UI and business consumers depend only on the facade.

**Acceptance Scenarios**:

1. **Given** the development implementation is selected, **When** the Telegram app requests verification, authorization, or permissions, **Then** it communicates only through the defined facade and does not access implementation internals.
2. **Given** the identity-service adapter is selected, **When** the Telegram app authenticates, **Then** the adapter delegates through identity-api and maps its result to the same application-facing facade contract without UI or business-flow changes.
3. **Given** either implementation produces an authorization result, **When** its outcome is evaluated, **Then** it satisfies the same application-facing authorization, permission-enforcement, safe-error, and privacy rules; identity-api responses additionally satisfy the configurable external-response permission limits, while the development implementation uses its fixed internal permission catalog.
4. **Given** development mode is explicitly enabled, **When** the application starts, **Then** only the development facade is active.
5. **Given** development mode is false or unspecified, **When** the application starts, **Then** only the identity-service facade is active.
6. **Given** runtime wiring provides zero or multiple facade implementations, **When** the application starts, **Then** startup fails before the app accepts users.

---

### User Story 3 - See a Permission-Appropriate Experience (Priority: P3)

As an authorized user, I want the app's navigation and available actions to match my current permissions so that I see a simple experience relevant to my role and cannot invoke restricted capabilities.

**Why this priority**: Permission-aware presentation is the primary user value after secure entry and reduces confusion while reinforcing access control.

**Independent Test**: Evaluate representative permission sets, including no permissions, one permission, multiple permissions, and unknown permissions, and verify that each produces exactly the expected visible destinations and enabled actions; for every allowed primary action, verify it is visible, clearly labeled, keyboard-focusable, and startable within two user interactions.

**Acceptance Scenarios**:

1. **Given** an authorized user has a defined set of permissions, **When** the landing experience loads, **Then** only destinations and actions associated with those permissions are visible and enabled.
2. **Given** an authorized user has no application permissions, **When** the landing experience loads, **Then** the user sees an empty-access explanation and guidance rather than restricted content.
3. **Given** a user attempts to reach a destination or action outside the visible navigation, **When** the request is evaluated, **Then** authorization is checked for that specific operation and access is denied if permission is absent.
4. **Given** the secure service returns an unknown permission, **When** the experience is assembled, **Then** the unknown permission grants no capability and does not prevent recognized permissions from working.
5. **Given** an authenticated principal has a null `sub` and non-empty permissions, **When** navigation or a protected action is evaluated, **Then** all permissions are ignored and the user receives the no-access experience.

---

### User Story 4 - Recover from Authorization Service Problems (Priority: P4)

As a user, I want clear and safe feedback when authorization cannot be confirmed so that I know whether to retry without seeing stale, fabricated, or sensitive information.

**Why this priority**: The app depends on an external trust authority, so predictable failure behavior is necessary for user confidence and safe operation.

**Independent Test**: Simulate timeout, temporary unavailability, incompatible response, and permission changes followed by reauthentication or authenticated-session refresh; verify safe denial, actionable feedback, refreshed permissions, and successful recovery after the service returns.

**Acceptance Scenarios**:

1. **Given** the secure service is temporarily unavailable, **When** a user opens the app or starts a protected action, **Then** the app does not assume access, performs no automatic retry, and offers an explicit retry without exposing internal details after the prior attempt has definitively completed.
2. **Given** a user's permissions change in the identity service, **When** protected access is evaluated before reauthentication or authenticated-session refresh, **Then** the current authenticated principal remains authoritative; **When** reauthentication or authenticated-session refresh completes, **Then** the refreshed principal controls access and newly forbidden content or actions are no longer available.
3. **Given** the secure service returns an incompatible or incomplete result, **When** the app processes it, **Then** access is denied safely and the user sees a recoverable error state.

### Edge Cases

- An otherwise valid authorized principal has a null `sub`; the session may be established, but all returned permissions are ignored and every protected capability denies safely. If the returned permission list is non-empty, the system emits a privacy-safe warning without identity, payload, credential, upstream-detail, or permission values. False consent remains valid and does not suppress permissions when `sub` is non-null. An empty non-null `sub` or another malformed principal field remains invalid.
- The raw Telegram authentication payload exceeds the configured positive size limit; the request is rejected before facade processing. The limit defaults to 32 KiB and has no application-enforced hard maximum.
- An identity-api permission list is empty, duplicated, contains unsupported values, exceeds the configured maximum unique-permission count, or contains a value longer than the configured maximum length. Duplicate occurrences are first reduced to unique values and produce one privacy-safe warning for the result without permission contents; duplication alone does not make the result incompatible. The count limit is evaluated after duplicate removal. An exceeded unique-count or length limit makes the result incompatible and denies access safely. The positive configurable limits default to 1,024 unique entries and 128 characters per permission and have no application-enforced hard ceilings. These configurable external-response limits do not apply to the development implementation's fixed internal permission catalog.
- The raw identity-api response exceeds the configured positive total response limit; it is rejected as incompatible before parsing. The limit defaults to 256 KiB and has no application-enforced hard ceiling.
- An authentication-payload, raw identity-api total-response, permission-count, or permission-length limit is configured as zero, negative, or malformed; application startup fails with a non-sensitive configuration error before any authentication request is accepted.
- The authenticated session ends while the user is viewing the app or submitting an action; subsequent protected access fails closed until reauthentication.
- A user opens a stale bookmark or constructs a direct request to a restricted destination.
- The same action is submitted more than once because of retries or repeated taps.
- An authorization request times out or fails after its outcome becomes uncertain; the application performs no automatic retry and enables explicit retry only after the prior attempt has definitively completed.
- The secure service responds slowly, is unavailable, or returns a response incompatible with the pinned facade or identity-api artifact version.
- A narrow mobile viewport, long translated labels, keyboard-only navigation, or a wider display changes the available space.
- Operational diagnostics are needed for a failure but personal data and Telegram identity must remain excluded.
- UI or business code accidentally depends on development-facade internals, identity-api transport details, or a secure-service personal-data model.
- The identity-service adapter differs from the development implementation in validation, authorization, permission, timeout, or error behavior.
- Runtime wiring activates no facade or more than one facade, preventing a single authoritative implementation from being selected; an absent development-mode property is valid and selects the identity-service facade.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST use the designated secure service as the sole authority for Telegram verification, user identity fields, consent, and permissions.
- **FR-002**: The system MUST provide one explicit boundary through which application behavior obtains authenticated authorization results and permissions from the secure service. The facade and identity-api dependencies MUST be pinned and versioned as build artifacts using semantic versioning; requests and responses MUST NOT carry a separate `contractVersion` field.
- **FR-003**: The system MUST reject missing, expired, malformed, unauthenticated, structurally incomplete, or incompatible secure-service results. The raw identity-api response MUST have a positive configurable total size limit, defaulting to 256 KiB with no application-enforced hard ceiling, and MUST be rejected as incompatible before parsing when it exceeds that limit. Identity-api response unique-permission count and individual permission length MUST each have a positive configurable limit, defaulting to 1,024 unique entries and 128 characters respectively, with no application-enforced hard ceilings. Duplicate identity-api permission occurrences MUST be reduced to unique values before the count limit is evaluated and MUST emit one privacy-safe warning for the result without permission contents; duplication alone MUST NOT make the result incompatible. An identity-api result exceeding the configured unique-count or length limit is incompatible. These configurable permission limits MUST NOT apply to the development implementation's fixed internal permission catalog. A null `sub` or false consent flag supplied in an otherwise valid authorized principal MUST NOT by itself make the result incomplete or malformed; an empty non-null `sub` remains malformed.
- **FR-004**: The system MUST represent an authorized user with the authenticated principal supplied by the secure service: nullable opaque string `sub`, optional `name`, permissions, consent, and application authentication flow. It MUST preserve a null `sub` and false consent flag as supplied. False consent MUST NOT suppress recognized permissions when `sub` is non-null. When `sub` is null, all returned permissions MUST be ignored for authorization and no protected capability may be exposed. Outside the secure-service boundary, the sole permitted Telegram-data handling is request-scoped transport of one raw `initData` value directly from the authentication callback to `redeemAuthorizationGrant`. The value MUST be rejected before facade processing when it exceeds a configured positive size limit; the limit defaults to 32 KiB and has no application-enforced hard maximum. Consumers MUST treat the value as opaque and MUST NOT parse, inspect, retain, display, persist, log, derive identity from it, place it in session state, or reuse it after the facade call completes.
- **FR-005**: The system MUST determine visible navigation, destinations, and actions from recognized permissions supplied by the secure service only when the authenticated principal has a non-null `sub`.
- **FR-006**: The system MUST treat missing and unknown permissions as granting no capability.
- **FR-007**: The system MUST require a non-null secure-service-supplied `sub` and the permission required for the specific operation before authorizing every protected destination or operation, regardless of whether the user interface hides it. A null `sub` MUST deny safely before idempotency state, ownership association, or another protected effect is created.
- **FR-008**: The system MUST deny access safely when verification or authorization cannot be confirmed and MUST NOT show protected content from an earlier principal. It MUST NOT automatically retry authorization requests; it MAY offer an explicit user-triggered retry only after the prior attempt has definitively completed.
- **FR-009**: The system MUST present distinct, concise, and non-sensitive states for loading, no access, authorization denied, temporary unavailability, incompatible response, and retry.
- **FR-010**: The system MUST reject protected access when the current authenticated session or its principal is missing or malformed. A null `sub` or false consent flag is not malformed by itself, but a null `sub` MUST deny every protected capability regardless of returned permissions. With a non-null `sub`, false consent MUST NOT suppress recognized permissions. The current authenticated principal MUST remain authoritative until reauthentication or authenticated-session refresh, at which point changed identity fields and permissions MUST take effect; per-operation secure-service reauthorization is not required.
- **FR-011**: The system MUST prevent repeated taps, retries, or delayed responses from causing a protected operation to execute more than once when duplication could cause harm.
- **FR-012**: The primary experience MUST be usable on narrow Telegram mobile screens and MUST remain readable and operable on wider screens, with keyboard-accessible semantic controls, adequate touch targets, clear focus, and meaning not conveyed by color alone.
- **FR-013**: The system MUST expose sufficient non-personal operational status to distinguish successful authorization, safe denial, timeout, unavailability, and incompatible secure-service responses without recording personal or Telegram identity data. It MUST emit a warning when identity-api returns a null `sub` with non-empty permissions and a warning when an identity-api result contains duplicate permissions. These warnings MUST NOT include identity, payload, credential, upstream-detail, or permission values.
- **FR-014**: The initial release MUST be limited to secure entry, permission-based navigation, representative protected destinations/actions, safe failure/retry states, and selectable development/identity-service facade implementations. Personal-data viewing or editing, permission administration, alternate authentication, independently hosted transport extraction, migration execution, rollback rehearsal, and extracted-service equivalence testing are out of scope.
- **FR-015**: During development preparation, the secure-service implementation MUST be allowed to reside temporarily in this repository as a distinct secure-service boundary, separate from Telegram presentation and application business behavior.
- **FR-016**: Only the secure-service boundary MAY parse Telegram identity data or own verification behavior and authorization policy. The Telegram authentication callback MAY transiently transport the bounded opaque `initData` request described by FR-004 into that boundary. Consumers MAY retain the returned authenticated principal only in the server-side authenticated session/cache; they MUST NOT persist, render, or log its `sub` or `name`.
- **FR-017**: The facade MUST define the complete application-facing contract intended for a future external secure-service integration, including requests, successful results, validation failures, authorization failures, unavailable states, and compatibility behavior. Adapter contract tests MUST verify compatibility with the pinned facade and identity-api artifact versions. A breaking contract change MUST increment the artifact major version and provide a migration and rollback plan.
- **FR-018**: All consumers MUST use the facade contract and MUST NOT depend on the temporary implementation's internal behavior, personal-data model, storage, or repository location.
- **FR-019**: The development and identity-service facade implementations MUST satisfy the same application-facing authorization, permission-enforcement, security-boundary, and privacy responsibilities for their supported outcomes. Configurable unique-permission count and permission-length validation is an identity-api response-boundary responsibility and MUST NOT constrain the development implementation's fixed internal permission catalog.
- **FR-020**: Application configuration MUST select exactly one facade implementation: `rg.secure-service.enabled=true` selects the development implementation, while `false` or an absent property selects the identity-service implementation. Runtime wiring that provides zero or multiple facade implementations MUST fail application startup.
- **FR-021**: UI and business consumers MUST require no changes when selection switches between the development and identity-service facade implementations. A separately specified future extraction MUST preserve this invariant and define its own migration, rollback, ownership, and independently hosted compatibility evidence.
- **FR-022**: The initial release MUST fully support Ukrainian as the default locale and English as a selectable locale; missing translations and unsupported locale input MUST fall back deterministically to Ukrainian.
- **FR-023**: Each new application session MUST start in Ukrainian, MUST allow the user to select English manually, and MUST retain that selection only for the current session without deriving a locale from Telegram or browser identity data or writing the selection to persistent client storage.
- **FR-024**: When the user changes the locale, the system MUST refresh all user-facing and assistive text in the current view immediately while preserving the authenticated session, current permissions, and current destination.
- **FR-025**: The configured authentication-payload limit, raw identity-api total-response limit, and identity-api response unique-permission-count and permission-length limits MUST be positive and well formed. A zero, negative, or malformed configured value MUST fail application startup with a non-sensitive error; absent values MUST use their specified defaults. The application MUST read and validate one immutable limit snapshot at startup; configuration changes MUST require restart and MUST NOT alter in-flight requests.

### Requirement Acceptance Coverage

- **FR-001–FR-004** are accepted through User Story 1 scenarios and its invalid-result independent test.
- **FR-005–FR-007** are accepted through User Story 3 scenarios across representative permission sets and direct restricted requests.
- **FR-008–FR-010** are accepted through User Story 4 scenarios for denial, dependency failure, response incompatibility, session loss, and permission changes before and after reauthentication or authenticated-session refresh.
- **FR-011** is accepted by repeating a potentially harmful action and verifying one business outcome.
- **FR-012** is accepted by completing all primary flows on representative narrow and wide viewports using touch and keyboard input.
- **FR-013** is accepted by diagnosing each defined authorization outcome, the null-sub/non-empty-permissions warning, and the duplicate-permission warning using operational evidence that contains no personal, Telegram identity, payload, credential, upstream-detail, or permission values.
- **FR-014** is accepted by confirming each named initial capability is demonstrable and each excluded capability is unavailable.
- **FR-015–FR-021** are accepted through User Story 2 by running the shared application-facing contract against the development and identity-service facade implementations, proving exactly-one implementation selection, and confirming consumers have no implementation dependency. Actual transport extraction and its migration/rollback rehearsal require a separate feature.
- **FR-022–FR-024** are accepted by completing every primary flow in Ukrainian and English, exercising missing-key and unsupported-locale fallback, verifying that a manual selection survives navigation within one session but a new session starts in Ukrainian, and confirming that a locale change refreshes the current destination without reauthentication or permission changes.
- **FR-025** is accepted by proving absent limit settings use their defaults and every zero, negative, or malformed limit prevents application startup without exposing sensitive configuration data.

### Key Entities

- **Authorization Result**: The authenticated principal supplied by the secure service. It preserves identity-api's nullable opaque `sub`, optional `name`, permission set, and consent flag, and adds the authentication flow used by this application.
- **Subject (`sub`)**: The optional opaque string identifier supplied by identity-api for authorization, ownership, idempotency binding, and business association; application code does not parse or display it. Every protected capability requires it and denies safely when it is absent.
- **Permission**: A recognized business capability granted by the secure service and mapped to one or more visible destinations or protected actions. For identity-api responses, its maximum character length and the maximum number of unique entries after duplicate removal are positive configurable limits, defaulting to 128 characters and 1,024 unique entries respectively, with no application-enforced hard ceilings. The development implementation instead returns values from its fixed internal permission catalog.
- **Protected Capability**: A destination or action with an explicit required permission and safe behavior when access is absent or cannot be confirmed.
- **User Experience State**: The current visible state, such as loading, permitted content, no access, denied, temporarily unavailable, incompatible response, or retry.
- **Secure-Service Contract**: The semantically versioned application-facade artifact shared by the development and identity-service implementations, defining accepted requests, authorization and permission results, failures, and compatibility without exposing transport or personal-data internals. Compatibility is established through pinned artifact versions and adapter contract tests rather than DTO version fields.
- **Development Secure-Service Implementation**: The explicitly enabled development-only implementation that verifies Telegram data, creates a temporary subject, and supplies development permissions without identity mapping or personal-data persistence.
- **Identity-Service Adapter**: The default production-oriented facade implementation that delegates authentication to identity-api and maps authenticated output into the application contract without exposing transport details to consumers.
- **Identity-API Raw Response**: The unparsed external response consumed by the identity-service adapter. Its total size has a positive configurable limit that defaults to 256 KiB, has no application-enforced hard ceiling, and is enforced before parsing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Authorization-service connections and requests use finite, validated timeout settings that keep dependency-failure handling within the ten-second safe-state deadline in SC-007; end-to-end landing time is not an acceptance measurement for this feature.
- **SC-002**: In acceptance testing, 100% of unauthorized, expired, malformed, structurally incomplete, and incompatible authorization results are denied without displaying protected content. Raw identity-api responses exceeding the configured total response limit are incompatible and rejected before parsing. Parsed identity-api results exceeding the configured unique-permission-count or permission-length limit are incompatible, while results exactly at each configured limit remain eligible for authorization when otherwise valid. Duplicate identity-api permission occurrences are reduced before count validation without making an otherwise valid result incompatible. This identity-api boundary behavior MUST pass using the 256 KiB, 1,024-unique-entry, and 128-character defaults and at least one valid custom positive value for each limit; development results continue to use the fixed internal permission catalog independently of those settings. An otherwise valid principal may remain authenticated when `sub` is null or consent is false, but 100% of protected capabilities deny when `sub` is null, and false consent does not suppress recognized permissions when `sub` is non-null.
- **SC-003**: For every tested non-null-sub principal and permission set, 100% of visible destinations and actions match the expected recognized permissions and 100% of direct unauthorized attempts are denied; for every null-sub principal, zero protected destinations or actions are available regardless of returned permissions.
- **SC-004**: For every tested permission set with an allowed primary action, the action is visible, clearly labeled, keyboard-focusable, and startable from the permission-appropriate landing experience within no more than two user interactions.
- **SC-005**: All primary flows can be completed at representative narrow and wide viewport sizes using both touch and keyboard input, with no hidden required controls or horizontal page scrolling.
- **SC-006**: In privacy review, zero personal-data or Telegram-identity values are found outside the isolated secure-service boundary, including Telegram application storage, client storage, screens, logs, metrics, traces, analytics, error reports, or support artifacts.
- **SC-007**: In simulated dependency failures, 100% of users receive a safe, actionable state within 10 seconds, no automatic authorization retry occurs, and users can recover through an explicit retry after service restoration without restarting the app.
- **SC-008**: Duplicate-submission testing produces no more than one business outcome for each protected operation where repetition could cause harm.
- **SC-009**: Every application-facing contract scenario supported by both implementations produces equivalent authorization, permission, validation, failure, and privacy outcomes against the development and identity-service facade implementations.
- **SC-010**: Configuration-selection testing activates only the development facade when explicitly enabled and only the identity-service facade when the development-mode property is false or absent; 100% of zero-facade and multiple-facade wiring scenarios fail startup.
- **SC-011**: Architecture and privacy review finds zero direct dependencies from the Telegram app or business consumers on either facade implementation, identity-api transport details, personal-data models, or storage.
- **SC-012**: Every primary flow, authorization state, navigation label, action, validation message, accessibility label, and notification is available in Ukrainian and English, with zero raw message keys or blank text shown when Ukrainian fallback is exercised.
- **SC-013**: After a locale change, 100% of visible and assistive text on the current destination uses the selected locale before the next user action, with no reauthentication, navigation reset, or permission change.
- **SC-014**: In every tested identity-api response with a null `sub` and non-empty permissions, exactly one privacy-safe warning is emitted for the authorization result and contains no identity, payload, credential, upstream-detail, or permission values.
- **SC-015**: Authentication payloads at the configured size limit are accepted for facade evaluation and payloads one byte larger are rejected before facade processing, using both the 32 KiB default and at least one valid custom positive limit.
- **SC-016**: For the authentication-payload limit, raw identity-api total-response limit, and each identity-api response permission limit, absent configuration uses the specified default, while 100% of tested zero, negative, and malformed values prevent application startup with no sensitive value exposed in the failure output. Valid changes take effect only after restart, and in-flight requests continue using the startup snapshot.
- **SC-017**: Every tested identity-api result containing duplicate permission occurrences continues with unique values, evaluates the configured count limit after duplicate removal, and emits exactly one privacy-safe duplicate-permission warning containing no identity, payload, credential, upstream-detail, or permission values.
- **SC-018**: Raw identity-api responses at the configured total size limit remain eligible for parsing, while responses one byte larger are rejected as incompatible before parsing, using both the 256 KiB default and at least one valid custom positive limit.

## Assumptions

- During development preparation, the development implementation is co-located in this repository as a distinct verification boundary but owns no personal-data storage or identity mapping; the identity service remains authoritative for production identity and permissions.
- The authenticated application session defines authorization lifetime. The app stores only the opaque principal in that session and enforces its permissions without creating a parallel identity or permission authority; a principal with null `sub` has no protected access regardless of returned permissions.
- The initial app uses a small, predefined catalog of permissions mapped to representative destinations and actions; managing roles or permissions belongs to the secure service and is outside this feature.
- Personalized UI means customization by authorization and permission only, not by personal attributes, profile information, or behavioral tracking.
- Users have a supported Telegram client and intermittent network failures are expected; safe retry is required, while offline protected operation is not.
- Actual independently hosted transport extraction, migration, rollback rehearsal, and extracted-service equivalence testing are deferred to a separate future feature.
- The facade is the stable application-facing boundary; both current implementations provide deterministic success and failure outcomes for compatibility validation without exposing their internals.
