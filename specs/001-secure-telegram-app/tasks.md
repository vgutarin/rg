# Tasks: Secure Permission-Aware Telegram App

**Input**: Design documents from `/specs/001-secure-telegram-app/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`, and `.specify/memory/constitution.md`

**Tests**: Unit, integration, contract, privacy, localization, responsive, and failure-path coverage
are mandatory because the specification and constitution require them. For changed behavior, add or
update the listed tests first and confirm that they fail for the intended reason before implementation.

**Organization**: Tasks are grouped by user story. The list describes the remaining implementation
delta on top of the existing two-module application and keeps each story independently testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run in parallel because it targets different files and has no dependency on an
  incomplete task in the same phase
- **[Story]**: Maps the task to User Story 1, 2, 3, or 4
- Every task includes an exact repository-relative file path

---

## Phase 1: Setup and External Capability Gate

**Purpose**: Establish the dependency and safe local-configuration prerequisites without expanding
the feature into identity-client development or external-service extraction.

- [ ] T001 Verify that a locally resolvable published non-snapshot identity-api/identity-rest-client release exposes finite connect/read/request timeouts, pre-parse raw-response limiting, zero automatic authentication retries, and duplicate-permission visibility, then pin that exact release in `gradle.properties`; leave this gate incomplete and record the missing capability in `specs/001-secure-telegram-app/quickstart.md` if no such release is available
- [X] T002 [P] Set the intended semantic facade artifact version and confirm `rg-logic` publication uses it without dynamic version notation in `gradle.properties` and `rg-logic/build.gradle`
- [X] T003 [P] Add a credential-free local configuration template with development mode, loopback identity URL, unmistakably fake identity key, timeout examples, and the four limit properties in `rg-frontend-vaadin/src/main/resources/application-local.example.properties`, while retaining local-file exclusions in `.gitignore`

**Checkpoint**: Exact compatible artifacts are pinned, or the production identity path is explicitly
blocked; local setup requires no real identity credential in source control.

---

## Phase 2: Foundational Contract and Limit Infrastructure

**Purpose**: Align the shared request/principal contract and immutable startup configuration before
implementing any story-specific behavior.

**⚠️ CRITICAL**: No changed user-story behavior is complete until this phase passes.

### Foundational tests

- [X] T004 [P] Add configuration-binding tests for the `32KB` init-data default, a custom positive value, and zero/negative/malformed startup failure without value disclosure in `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationLimitsPropertiesTest.java`
- [X] T005 [P] Add UTF-8 byte-boundary tests proving init data at the configured limit proceeds and one byte over is rejected before facade invocation for default and custom limits in `rg-logic/src/test/java/vg/rg/security/TelegramAuthorizationRequestValidatorTest.java`
- [X] T006 [P] Update contract-model and serialization tests for nullable `sub`, empty non-null `sub`, false consent, immutable syntactically valid permissions without identity-specific fixed count/length limits, and absence of DTO `contractVersion` fields in `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationContractModelTest.java`
- [X] T007 [P] Update common fixtures and facade conformance to cover established/provisional principals and shared closed outcomes without requiring every adapter to produce every outcome in `rg-logic/src/test/java/vg/rg/security/support/SecureAuthorizationFixtures.java` and `rg-logic/src/test/java/vg/rg/contract/SecureAuthorizationFacadeContract.java`

### Foundational implementation

- [X] T008 [P] Implement immutable startup binding for positive `rg.secure-service.max-init-data-size` with a `32KB` default and non-sensitive validation errors in `rg-logic/src/main/java/vg/rg/security/SecureAuthorizationLimitsProperties.java`
- [X] T009 Implement shared nonblank and UTF-8 byte-length validation using the startup snapshot in `rg-logic/src/main/java/vg/rg/security/TelegramAuthorizationRequestValidator.java`
- [X] T010 Remove the hard-coded 16 KiB ceiling while retaining structural request validation in `rg-logic/src/main/java/vg/rg/security/model/TelegramInitDataRequest.java`
- [X] T011 Wire shared request validation before the selected facade is invoked and map rejected payloads to a closed safe outcome in `rg-logic/src/main/java/vg/rg/security/AuthorizationApplicationService.java`
- [X] T012 [P] Keep common principal validation limited to nullable-field bounds, permission syntax, and immutable copying so identity-only configurable limits can be enforced by the identity adapter in `rg-logic/src/main/java/vg/rg/security/model/AuthenticatedUserPrincipal.java`

**Checkpoint**: Both facade paths receive only startup-bounded input, and the common principal no
longer embeds identity-response policy.

---

## Phase 3: User Story 1 - Enter the App Securely (Priority: P1) 🎯 MVP

**Goal**: Establish sessions only from structurally valid facade results, preserve nullable identity
fields and false consent, and expose no protected capability when `sub` is null.

**Independent Test**: Feed established, null-sub, false-consent, denied, invalid, expired,
unavailable, incompatible, and oversized results through secure entry. Only valid authorized results
establish a session; null-sub sessions receive no authorities or protected content, and all failures
remain localized and non-sensitive.

### Tests for User Story 1

- [X] T013 [P] [US1] Extend development-facade tests for verified input, temporary decimal-string subject, fixed recognized permission catalog, true consent, Telegram flow, and no identity persistence in `rg-logic/src/test/java/vg/rg/security/dev/DevSecureAuthorizationFacadeTest.java`
- [X] T014 [P] [US1] Extend identity-adapter tests for established mapping, null-sub/false-consent mapping, empty/null/malformed results, privacy-safe exception mapping, and exactly one value-free warning for null `sub` with non-empty permissions in `rg-logic/src/test/java/vg/rg/security/identity/IdentitySecureAuthorizationFacadeTest.java`
- [X] T015 [P] [US1] Add session tests proving all identity fields serialize, null-sub principals establish authentication with zero authorities, false consent with non-null `sub` retains recognized authorities, replacement clears prior state, and logout removes session identity fields in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/ApplicationSecurityContextServiceTest.java`
- [X] T016 [P] [US1] Extend secure-entry view tests for bounded callback forwarding, established and null-sub authorization, invalid/expired/denied/unavailable/incompatible states, duplicate callback suppression, no protected-content flash, and safe localized retry in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/telegram/TelegramAuthViewTest.java`
- [X] T017 [P] [US1] Add a Spring/Vaadin secure-entry integration test proving an established principal reaches permitted content while a null-sub principal reaches no-access with no rendered identity values in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/SecureEntryIntegrationTest.java`

### Implementation for User Story 1

- [X] T018 [US1] Preserve identity-api `sub`, `name`, consent, and validated permissions, map null/malformed/runtime results to closed outcomes, remove dormant principal logging, and emit one value-free warning for null `sub` with non-empty permissions in `rg-logic/src/main/java/vg/rg/security/identity/IdentitySecureAuthorizationFacade.java`
- [X] T019 [US1] Install the complete typed principal but derive zero Spring authorities when `sub` is null, preserve false consent behavior, and clear old authentication before failed replacement in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/security/ApplicationSecurityContextService.java`
- [X] T020 [US1] Align Telegram callback redemption and navigation so a valid null-sub result establishes the session and proceeds to the no-access experience while invalid/expired/denied outcomes fail closed in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/telegram/TelegramAuthView.java`
- [X] T021 [US1] Add or align Ukrainian-default and English secure-entry/no-access messages without identity, payload, or upstream detail in `rg-frontend-vaadin/src/main/resources/messages.properties` and `rg-frontend-vaadin/src/main/resources/messages_en.properties`

**Checkpoint**: Secure entry independently handles established and provisional identity results;
null-sub sessions are valid but globally unprivileged.

---

## Phase 4: User Story 2 - Preserve the External-Service Boundary (Priority: P2)

**Goal**: Keep both implementations behind one facade, select exactly one at startup, and validate
all untrusted identity transport and permission output through explicit bounded contracts.

**Independent Test**: Run common conformance against both implementations, start real contexts for
true/false/missing facade selection, and exercise identity success, empty/malformed/oversized output,
duplicates, timeout, and connection failure through the pinned client with no automatic retry.

### Tests for User Story 2

- [X] T022 [P] [US2] Align development-specific conformance with the shared facade suite while proving the fixed development catalog is unaffected by identity permission settings in `rg-logic/src/test/java/vg/rg/contract/DevSecureAuthorizationFacadeContractTest.java`
- [X] T023 [P] [US2] Add identity-adapter conformance for shared invariants and only the outcomes identity-api can represent in `rg-logic/src/test/java/vg/rg/contract/IdentitySecureAuthorizationFacadeContractTest.java`
- [X] T024 [P] [US2] Add identity-response validation tests for absent/default/custom positive limits, exact unique-count and character-length boundaries, one-over rejection, deduplication before count, one value-free duplicate warning, malformed permissions, and development-catalog isolation in `rg-logic/src/test/java/vg/rg/security/identity/IdentityAuthorizationResponseValidatorTest.java`
- [X] T025 [P] [US2] Extend component-selection tests for true, false, and missing `rg.secure-service.enabled`, plus zero/multiple facade startup failure, in `rg-logic/src/test/java/vg/rg/security/SecureServiceComponentWiringTest.java`
- [X] T026 [P] [US2] Add application-context tests for defaults, valid custom values, zero/negative/malformed identity permission limits, fake-key development startup, and false/missing identity selection in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/SecureServiceApplicationContextTest.java`
- [ ] T027 [P] [US2] Add loopback HTTP integration tests for raw responses exactly at and one byte over default/custom limits, duplicate visibility, finite connect/read/request timeouts, connection failure, no automatic retry, and safe outcome mapping in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/IdentitySecureAuthorizationIntegrationTest.java`
- [X] T028 [P] [US2] Extend architecture tests to prohibit UI/business dependencies on facade implementations, identity transport internals, REST/OpenAPI production types, request/response version fields, and sensitive logging in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/FrontendSecurityArchitectureTest.java` and `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationContractArchitectureTest.java`

### Implementation for User Story 2

- [X] T029 [P] [US2] Bind immutable positive `rg.secure-service.identity.max-permission-count` and `rg.secure-service.identity.max-permission-length` settings with defaults `1024` and `128` and non-sensitive startup failures in `rg-logic/src/main/java/vg/rg/security/identity/IdentityAuthorizationLimitsProperties.java`
- [X] T030 [US2] Implement identity-only permission normalization, duplicate detection/warning, post-deduplication count checking, per-value length checking, syntax validation, and incompatible-result signaling in `rg-logic/src/main/java/vg/rg/security/identity/IdentityAuthorizationResponseValidator.java`
- [ ] T031 [US2] Integrate the identity-response validator and the pinned client's duplicate indicator/raw occurrence contract before principal construction in `rg-logic/src/main/java/vg/rg/security/identity/IdentitySecureAuthorizationFacade.java`
- [X] T032 [P] [US2] Align conditional Spring selection so true creates only the development facade and false or missing creates only the identity facade in `rg-logic/src/main/java/vg/rg/security/dev/DevSecureAuthorizationFacade.java` and `rg-logic/src/main/java/vg/rg/security/identity/IdentitySecureAuthorizationFacade.java`
- [ ] T033 [US2] Configure `vg.identity.rest-client.max-response-size=256KB`, finite timeout defaults within the ten-second safe-state deadline, environment-sourced real credentials, and the RG limit defaults in `rg-frontend-vaadin/src/main/resources/application.properties`
- [X] T034 [US2] Keep the facade API in `rg-logic`, the transport client in `rg-frontend-vaadin`, and exact pinned dependency coordinates without adding a REST module in `rg-logic/build.gradle` and `rg-frontend-vaadin/build.gradle`

**Checkpoint**: Both adapters satisfy the shared boundary, identity-specific limits are enforced at
the correct layer, and production readiness remains blocked unless T001 and the transport tests pass.

---

## Phase 5: User Story 3 - See a Permission-Appropriate Experience (Priority: P3)

**Goal**: Require both non-null `sub` and a recognized current-session permission for every protected
route/action, while keeping unknown permissions inert and repeated actions idempotent.

**Independent Test**: Exercise established and null-sub principals with no, recognized, unknown, and
changed permissions. Null-sub principals expose zero protected capabilities; direct route and method
access denies without effects; authenticated-principal replacement applies new permissions atomically.

### Tests for User Story 3

- [X] T035 [P] [US3] Update authority tests so every recognized permission still denies when `sub` is null, false consent does not deny when `sub` is non-null, unknown requirements deny, malformed/missing authentication denies, and `currentSubject` remains opaque in `rg-logic/src/test/java/vg/rg/security/AuthorityCheckerTest.java`
- [X] T036 [P] [US3] Add service tests proving null-sub denial returns a stable result before idempotency state/effect creation, same-subject retries execute once, and cross-subject key reuse denies in `rg-logic/src/test/java/vg/rg/service/ProtectedActionServiceImplTest.java`
- [X] T037 [P] [US3] Add Spring proxy tests proving missing permission and null `sub` are denied by method authorization while a valid subject with permission executes once in `rg-logic/src/test/java/vg/rg/service/ProtectedActionMethodSecurityTest.java`
- [X] T038 [P] [US3] Extend session tests proving old permissions remain until atomic authenticated-principal replacement, new permissions apply immediately afterward, and locale changes/reloads do not refresh authorization in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/ApplicationSecurityContextServiceTest.java`
- [X] T039 [P] [US3] Extend shell tests for recognized-only navigation, null-sub suppression, unknown-permission inertness, localized labels, and no identity rendering in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/MainViewTest.java`
- [X] T040 [P] [US3] Extend protected-view tests for no permissions, each recognized permission, recognized-plus-unknown values, null-sub no-access routing, direct-route denial, stale-content removal, and session replacement in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/PermissionAwareViewsTest.java`
- [X] T041 [P] [US3] Add deterministic tests proving every allowed primary action is visible, clearly labeled, keyboard-focusable, and startable from landing within at most two activations in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/PrimaryActionInteractionTest.java`

### Implementation for User Story 3

- [X] T042 [US3] Require a current typed principal with non-null `sub` plus the recognized permission in `hasAuthority`, and expose the subject only through `currentSubject`, in `rg-logic/src/main/java/vg/rg/security/AuthorityChecker.java`
- [X] T043 [US3] Return a stable denied result before idempotency-state creation or effect invocation when the subject is absent, while retaining atomic single-effect replay behavior, in `rg-logic/src/main/java/vg/rg/service/ProtectedActionServiceImpl.java`
- [X] T044 [US3] Make authenticated-principal replacement atomic and keep permission refresh independent of browser reload and locale changes in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/security/ApplicationSecurityContextService.java`
- [X] T045 [US3] Build shell navigation only from recognized effective permissions of a non-null-sub principal and keep logout/locale controls mobile-safe in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/MainView.java`
- [X] T046 [US3] Render only recognized capabilities, route null-sub principals to no-access, preserve server-side route checks, and keep the idempotent primary action within two activations in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/LandingView.java`, `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/ReportsView.java`, and `rg-frontend-vaadin/src/main/resources/META-INF/resources/secure-telegram-aura.css`

**Checkpoint**: Navigation, routes, and protected methods all require subject plus permission;
null-sub denial happens before state or effects, and permission replacement is deterministic.

---

## Phase 6: User Story 4 - Recover from Authorization Service Problems (Priority: P4)

**Goal**: Present mutually exclusive, localized, accessible recovery states for bounded dependency
failure, session loss, and incompatible results without stale protected content or automatic retry.

**Independent Test**: Simulate loading, permitted, no-access, denied, unavailable, incompatible,
retrying, session loss, and restoration. Exactly one state is visible, protected content is removed
first, retry appears only after completion, and dependency failure reaches a safe state within ten
seconds.

### Tests for User Story 4

- [X] T047 [P] [US4] Add state-transition, stale-content removal, retry eligibility, focus, and polite-live-region tests for all seven authorization states in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/AuthorizationStatusComponentTest.java`
- [X] T048 [P] [US4] Extend authentication-view tests for one in-flight request, no automatic retry, explicit retry only after completion, incompatible non-retry guidance, and protected-content replacement in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/telegram/TelegramAuthViewTest.java`
- [X] T049 [P] [US4] Extend observability tests for success, denial, null-sub permissions, duplicates, timeout/unavailability, incompatible output, and retry while excluding payload, subject, name, credentials, upstream details, and permission values in `rg-logic/src/test/java/vg/rg/security/SecureAuthorizationObservabilityTest.java`
- [X] T050 [P] [US4] Add integration coverage proving configured transport deadlines and UI handling reach a safe state within ten seconds without measuring end-to-end landing latency in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/AuthorizationFailureDeadlineIntegrationTest.java`

### Implementation for User Story 4

- [X] T051 [P] [US4] Implement the seven-state enum and reusable localized semantic status composition with safe retry, heading focus, and polite live-region behavior in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/AuthorizationUiState.java` and `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/AuthorizationStatusComponent.java`
- [X] T052 [US4] Replace the private authentication state rendering with the reusable status composition and enforce explicit post-completion retry in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/telegram/TelegramAuthView.java`
- [X] T053 [P] [US4] Integrate the reusable no-access and denied states without raw diagnostics or stale content in `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/NoAccessView.java` and `rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/AccessDeniedErrorView.java`
- [X] T054 [P] [US4] Add complete Ukrainian-default and English keys for every state, retry, timeout, subject denial, focus label, and accessibility message in `rg-frontend-vaadin/src/main/resources/messages.properties` and `rg-frontend-vaadin/src/main/resources/messages_en.properties`
- [X] T055 [US4] Apply mobile-first status, focus, 44px touch-target, long-label, and reduced-motion styling with wider layouts only in `min-width` queries in `rg-frontend-vaadin/src/main/resources/META-INF/resources/secure-telegram-aura.css`

**Checkpoint**: Failure and recovery are bounded, fail-closed, localized, accessible, explicit, and
free of sensitive diagnostics.

---

## Phase 7: Polish and Cross-Cutting Validation

**Purpose**: Prove localization, privacy, responsiveness, artifact compatibility, and build health
across all completed stories.

- [X] T056 [P] Re-run fresh-session Ukrainian default, same-session locale retention, in-place locale refresh, missing-key fallback, bundle parity, no-facade-call, and selected-picker coverage in `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocaleSessionIntegrationTest.java`, `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocalizationBundleTest.java`, and `rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/LocaleRefreshIntegrationTest.java`
- [ ] T057 [P] Execute the Ukrainian/English, light/dark, keyboard/live-region, touch-target, long-label, horizontal-scroll, and two-interaction matrix at 320x640, 390x844, 768x1024, and 1280x800 and record actual results in `specs/001-secure-telegram-app/quickstart.md`
- [X] T058 [P] Audit UI, source, logs, metrics, traces, errors, screenshots, configuration, analytics, and support artifacts for Telegram payloads, identity fields, credentials, upstream details, and permission values, then record non-sensitive evidence in `specs/001-secure-telegram-app/quickstart.md`
- [X] T059 Verify exact non-snapshot facade/identity versions, client capability evidence, common/adapter contract compatibility, and migration/rollback documentation for any breaking change, then record unresolved external prerequisites in `specs/001-secure-telegram-app/quickstart.md`
- [X] T060 Run `./gradlew :rg-logic:test`, `./gradlew :rg-frontend-vaadin:test`, `./gradlew test`, `./gradlew check`, configured static/dependency/security checks, and `git diff --check`, then record only commands and results actually available in `specs/001-secure-telegram-app/quickstart.md`
- [X] T061 Reconcile final implementation evidence against FR-001 through FR-025 and SC-001 through SC-018, including exact/default/custom/invalid limit boundaries and the ten-second failure deadline, in `specs/001-secure-telegram-app/quickstart.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately. T001 is the external identity capability gate; T002 and
  T003 can proceed independently.
- **Phase 2 — Foundation**: Depends on T002 and T003, but not on the externally supplied client in
  T001; it blocks all changed story behavior.
- **User Story 1**: Depends on Phase 2 and is the secure-entry MVP.
- **User Story 2**: Depends on Phase 2 and cannot complete until T001 passes; its tests may be written
  against the required contract while the artifact is unavailable.
- **User Story 3**: Depends on the foundational principal contract and US1 session installation.
- **User Story 4**: Depends on the closed outcomes from US1/US2; status-component work may begin after
  Phase 2.
- **Phase 7 — Polish**: Depends on every story selected for delivery.

### User Story Completion Graph

```text
Setup + identity-client capability gate
                  |
             Foundation
             /        \
    US1 Secure Entry   US2 Facade Boundary
             \        /
       US3 Permission Experience
                  |
            US4 Recovery
                  |
       Cross-cutting validation
```

### Within Each User Story

- Add or update tests first and confirm the intended failure before production changes.
- Align configuration/models before adapters/services, then integrate views and application contexts.
- Never include real bot tokens, API keys, init data, subjects, names, upstream bodies, or permission
  values in fixtures, logs, screenshots, or evidence.
- Treat null `sub` as valid session structure but deny every protected capability regardless of
  permissions; false consent does not suppress recognized permissions when `sub` is present.
- Do not claim identity readiness until the pre-parse response limit, duplicate visibility, finite
  timeouts, and no-retry behavior are proven against a published client.
- Do not claim release compatibility from snapshot or dynamic artifact versions.

---

## Parallel Execution Examples

### User Story 1

```text
Parallel tests: T013, T014, T015, T016, T017
Join path: T018 -> T019 -> T020 -> T021
```

### User Story 2

```text
Parallel tests: T022, T023, T024, T025, T026, T027, T028
Join path: T029 -> T030 -> T031; T032 can proceed beside T029; then T033 -> T034
```

### User Story 3

```text
Parallel tests: T035, T036, T037, T038, T039, T040, T041
Join path: T042 -> T043; T044 and T045 can proceed in parallel; then T046
```

### User Story 4

```text
Parallel tests: T047, T048, T049, T050
Join path: T051; T053 and T054 can proceed in parallel; then T052 -> T055
```

---

## Implementation Strategy

### MVP First

1. Complete T002 and T003 plus Foundation; T001 may remain a recorded production-identity blocker
   while the development-facade MVP proceeds.
2. Complete User Story 1 secure entry.
3. Stop and independently validate established, null-sub, false-consent, and fail-closed outcomes.
4. Demo secure entry only; do not claim production identity transport, permission experience, or
   complete recovery readiness.

### Incremental Delivery

1. **Foundation + US1**: Secure-entry MVP with bounded input and null-sub global denial.
2. **Add US2**: Both adapters, exact selection, identity response limits, finite transport, and
   artifact compatibility.
3. **Add US3**: Subject-plus-permission enforcement, idempotency, session replacement, and the
   two-interaction primary action.
4. **Add US4**: Complete localized recovery-state behavior and the ten-second failure deadline.
5. **Polish**: Locale, viewport, privacy, dependency, and release evidence.

## Notes

- `[P]` tasks operate on different files and have no dependency on an incomplete task in the same
  phase.
- User-story tasks always carry their `[US#]` label; setup, foundation, and polish tasks do not.
- Existing passing behavior may be retained; changed behavior must first demonstrate the intended
  failing test.
- The four limit settings are immutable startup snapshots with defaults `32KB`, `256KB`, `1024`, and
  `128`; all accept positive custom values, have no application hard ceilings, and fail startup for
  zero, negative, or malformed values.
- Actual independently hosted extraction, transport schema, migration execution, rollback rehearsal,
  and extracted-service equivalence remain outside this feature.
- Any commit or MR text created during implementation must include its own
  `Assisted-by: Codex <noreply@openai.com>` trailer.
