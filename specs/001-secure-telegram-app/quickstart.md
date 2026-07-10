# Quickstart Validation Guide

This guide validates the planned feature end to end. Use synthetic credentials and payloads in
automated evidence; never copy real values into commands, reports, screenshots, or logs.

## Preconditions

1. Use Java 21 and the repository Gradle wrapper.
2. Review [the data model](data-model.md),
   [the facade contract](contracts/secure-authorization-facade.md), and
   [the UI contract](contracts/permission-aware-ui.md).
3. Use exact dependency versions. Snapshot `rg-logic` or identity artifacts are acceptable only for
   development; release compatibility evidence requires published non-snapshot versions.
4. For development-facade testing, set `rg.secure-service.enabled=true` and provide the bot token
   through an ignored `application-local.properties`, `.yml`, or `.yaml` file under an explicitly
   activated local profile. If identity-rest-client auto-configuration requires an API key, supply a
   clearly fake, non-secret local value and a loopback/non-production base URL; the development facade
   does not call that client.
5. For identity-facade testing, leave `rg.secure-service.enabled` absent or set it to false and supply
   identity base URL, API key, and finite timeout values through approved runtime secret/configuration
   sources. Use an identity client that supports explicit finite transport timeouts, a configurable
   pre-parse response limit, and duplicate-permission visibility.
6. Start with limit defaults: 32 KiB Telegram payload, 256 KiB raw identity response, 1024 unique
   identity permissions after deduplication, and 128 characters per identity permission. Use only
   positive custom values; limit changes require restart.

The corresponding properties are `rg.secure-service.max-init-data-size`,
`vg.identity.rest-client.max-response-size`,
`rg.secure-service.identity.max-permission-count`, and
`rg.secure-service.identity.max-permission-length`. Spring data-size defaults are `32KB` and
`256KB`, representing 32 KiB and 256 KiB; byte-boundary tests use UTF-8 for `initData` and raw bytes
for the identity response.

### Phase 1 identity-client capability gate (2026-08-26)

The locally available published `0.0.1` and `0.0.2` identity artifacts do not satisfy the production
gate. Bytecode signature inspection found that `0.0.1` exposes only base URL and API-key client
settings. Version `0.0.2` adds connect and read timeouts, but neither release exposes a pre-parse raw
response-size limit. Both published identity-api principals expose permissions only as a `Set`, so
duplicate occurrences are already lost before RG can detect or warn about them. No published version
that satisfies the gate was found; development currently resolves exact version `0.0.2`. Production
identity integration remains blocked until a published non-snapshot client provides every required
capability; development-facade work may continue independently.

### Phase 4 implemented boundary (2026-08-26)

RG now validates identity permission syntax, removes duplicates when occurrence information is
available, applies the unique-count limit after deduplication, and applies the per-permission length
limit before constructing the application principal. Defaults are `1024` and `128`; the supported
runtime overrides are `RG_SECURE_SERVICE_IDENTITY_MAX_PERMISSION_COUNT` and
`RG_SECURE_SERVICE_IDENTITY_MAX_PERMISSION_LENGTH`. `RG_SECURE_SERVICE_MAX_INIT_DATA_SIZE` overrides
the default `32KB` opaque Telegram input limit.

The published client is configured with finite supported defaults through
`VG_IDENTITY_REST_CLIENT_CONNECT_TIMEOUT` (`PT2S`) and
`VG_IDENTITY_REST_CLIENT_READ_TIMEOUT` (`PT8S`). Loopback integration tests cover successful mapping,
read timeout, server failure, privacy-safe failure mapping, and no application retry. Do not treat
`vg.identity.rest-client.max-response-size` or a total request-timeout setting as active controls in
RG: published client `0.0.2` does not expose them, and its `Set<String>` permissions contract cannot
preserve duplicate occurrences. T001, T027, T031, and T033 therefore remain release-blocking.

## Build Sequence

Run focused tests first:

```bash
./gradlew :rg-logic:test
./gradlew :rg-frontend-vaadin:test
```

Then run the complete suite and repository checks:

```bash
./gradlew test
./gradlew check
git diff --check
```

Expected: all model, facade, adapter, Spring wiring, authorization, idempotency, localization,
privacy, and component tests pass. Record the exact dependency versions and commands used. Do not
claim formatter, static-analysis, vulnerability, browser, or dependency checks that the build does
not actually configure.

## Run Locally

Activate an ignored local profile and start the frontend:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :rg-frontend-vaadin:bootRun
```

Open the configured Telegram Mini App URL. Do not paste real Telegram init data into a terminal or
test report. Automated tests use generated payloads and non-production keys.

## Scenario 1: Facade Selection and Identity Transport

Run real Spring application-context tests for each configuration:

| Configuration | Expected result |
|---|---|
| `rg.secure-service.enabled=true` | Exactly one development facade; an unused identity client may initialize with a fake non-secret local API key |
| `rg.secure-service.enabled=false` | Exactly one identity facade and configured identity client |
| Property missing | Exactly one identity facade and configured identity client |
| Missing or conflicting resulting beans | Startup fails |

For the identity path, verify configured connect/read/request timeouts are finite, positive, bounded,
and shorter than the ten-second UI failure deadline. Verify the raw-response, unique-permission-count,
and permission-length settings are positive startup snapshots. Simulate timeout, connection refusal,
null result, empty result, malformed principal, oversized response, duplicate permissions, and runtime
failure.

Expected:

- Timeout/client failure becomes `UNAVAILABLE` without upstream detail.
- Null or malformed successful output becomes `INCOMPATIBLE`.
- Empty identity-api result becomes `INVALID_REQUEST`.
- A raw identity response one byte over its configured limit becomes `INCOMPATIBLE` before parsing.
- Duplicate permissions are reduced to unique values, counted afterward, and produce exactly one
  privacy-safe warning with no identity or permission values.
- Unique-count or permission-length overflow becomes `INCOMPATIBLE` only on the identity path.
- Zero, negative, or malformed limit configuration fails startup without printing the value; absent
  configuration uses defaults and valid changes apply only after restart.
- No automatic Telegram-authentication retry occurs.
- Identity client auto-configuration may remain active in development, but the development facade
  never invokes it and its configured API key is explicitly fake and non-secret.

## Scenario 2: Secure Entry and Principal Fidelity

Exercise valid, invalid-signature, missing-hash, expired, future-dated, malformed, oversized,
bot-account, missing-user, unavailable, null-sub, and false-consent inputs.

Expected:

- The frontend forwards bounded init data directly to the selected facade without parsing,
  retaining, displaying, persisting, or logging it.
- The identity adapter preserves `sub`, `name`, and consent exactly as supplied, converts permission
  occurrences to the validated unique set required by the contract, and adds
  `authenticationFlow=TELEGRAM`.
- Null `sub` and false consent remain structurally valid when the principal is otherwise valid.
- Null `sub` ignores all permissions, grants no protected capability, and emits one privacy-safe
  warning when identity-api returned non-empty permissions. False consent does not suppress
  permissions when `sub` is non-null.
- An empty non-null `sub`, malformed permission, identity limit overflow, null result, or other
  structurally incompatible principal fails closed.
- The development facade uses the verified Telegram ID's decimal string as temporary `sub`, no
  display name, the development permission catalog, true consent, and Telegram flow.
- Java/session serialization preserves all principal fields, including null `sub` and false consent.
- No protected content appears before authorization succeeds.

## Scenario 3: Permissions, Session Refresh, and Subject Requirements

Test no permissions, each single recognized permission, all recognized permissions, unknown-only,
recognized-plus-unknown, malformed, wrong-case, and provisional principal cases.

Expected:

- Navigation and every protected route require non-null `sub` plus a recognized permission.
- Unknown permissions remain inert and do not suppress recognized values.
- Null `sub` exposes no protected content or action regardless of returned permissions; false consent
  leaves recognized access intact when `sub` is non-null.
- Any principal with null `sub` receives a controlled denial before idempotency state or protected
  effects, and no internal exception reaches the UI.
- Direct route and method invocation are independently denied when permission is absent.
- Identity-service permission changes do not affect the existing authenticated principal.
- Reauthentication or authenticated-session replacement atomically applies the new permissions.
- Locale change and browser reload do not themselves fetch new permissions.

## Scenario 4: Duplicate Safety and Recovery

Simulate repeated taps, repeated idempotency keys, a key reused by another non-null subject, timeout
after protected-effect dispatch, session loss, and service restoration.

Expected:

- The same subject/key produces at most one business effect and returns the prior safe result.
- A different subject cannot take ownership of an existing key.
- A missing subject is denied before the key is recorded.
- Ambiguous mutations are not automatically retried or fabricated as successful.
- Non-permitted states replace protected content before they render.
- Explicit retry recovers after restoration without exposing internal details.

## Scenario 5: Primary Action, Responsive Layout, and Accessibility

For every permission set with an allowed primary action, verify the action is visible, clearly
labeled, keyboard-focusable, and startable from the landing experience within at most two user
activations. Count click, tap, Enter, or Space; do not count passive page load, focus movement, or
scrolling.

Repeat the authorization states in Ukrainian and English, light and dark schemes, keyboard-only
navigation, long labels, and these viewports:

| Viewport | Layout | Keyboard/live region | Two-interaction primary action | Status |
|---|---|---|---|---|
| 320x640 | No horizontal scroll; overlay drawer | Not run | Not run | Browser runner unavailable |
| 390x844 | Mobile touch targets | Not run | Not run | Browser runner unavailable |
| 768x1024 | Coherent drawer/content transition | Not run | Not run | Browser runner unavailable |
| 1280x800 | Bounded content width | Not run | Not run | Browser runner unavailable |

Every touch action is at least 44px-equivalent, focus remains visible, tab order follows visual
order, state changes are announced, and meaning never depends on color alone.

## Scenario 6: Internationalization Lifecycle

Set browser `Accept-Language` to English and the JVM default to a non-Ukrainian locale.

Expected:

- A fresh session starts in Ukrainian regardless of browser, JVM, or Telegram locale.
- The picker exposes only localized Ukrainian and English options and always shows its selection.
- Selection rerenders all attached visible and assistive text through Vaadin locale propagation
  before the next action; it does not use `Page.reload()`.
- Route/query, authentication, principal, permissions, and session remain unchanged, and locale
  selection alone never invokes the facade.
- Same-session navigation/reload retains the selection; a new session resets to Ukrainian.
- Unsupported/null locale and missing English keys fall back to Ukrainian; a total miss produces a
  localized Ukrainian safety message rather than a raw key or blank.
- No locale value enters browser persistence, cookies, a database, the principal, or facade request.

## Scenario 7: Privacy, Versioning, and Architecture Audit

Run scoped source/configuration searches plus runtime log/metric/trace/error review.

Expected:

- No `initData`, Telegram identity, principal `sub`/name, API key, credential, or upstream body appears
  in UI, logs, metrics, traces, errors, screenshots, analytics, or support artifacts.
- UI and business code depend only on `SecureAuthorizationFacade`, not implementation or
  identity-rest-client internals.
- `rg-logic` has no frontend dependency.
- Common facade-contract tests run against both implementations; adapter suites cover their supported
  outcome distinctions without fabricating equivalence.
- `rg-logic`, identity-api, and identity-rest-client versions are exact and recorded. DTOs contain no
  `contractVersion` field.
- Any breaking facade change has a major artifact version, migration plan, rollback plan, and adapter
  compatibility evidence.

## Deferred Extraction Gate

Actual independently hosted extraction, transport schema, migration, rollback rehearsal, and
extracted-service equivalence are outside this feature. A future feature must define and validate
those concerns without changing current UI or business consumers.

## Phase 7 Validation Evidence (2026-08-26)

### Localization lifecycle

The focused locale suites passed for the Ukrainian fresh-session default, Ukrainian/English bundle
parity and nonblank values, unsupported/null and missing-key Ukrainian fallback, selected picker
state, in-place UI/session locale propagation, navigation and permission retention, persisted
principal retention, and absence of a secure-facade dependency in `MainView`. The focused commands
were:

```text
./gradlew :rg-frontend-vaadin:test --tests 'vg.rg.frontend.vaadin.service.LocaleSessionIntegrationTest' --tests 'vg.rg.frontend.vaadin.service.LocalizationBundleTest'
./gradlew :rg-frontend-vaadin:test --tests 'vg.rg.frontend.vaadin.LocaleRefreshIntegrationTest' --tests 'vg.rg.frontend.vaadin.MainViewTest' --tests 'vg.rg.frontend.vaadin.service.LocalizationServiceTest' --tests 'vg.rg.frontend.vaadin.security.ApplicationSecurityContextServiceTest'
```

Both commands completed successfully. The second command also proves locale changes do not replace
the authenticated principal or its permissions. No browser reload is used by the locale path.

### Browser and responsive matrix

No browser, Vaadin TestBench, Playwright, Selenium, or equivalent browser dependency/tool was
available in this workspace. The following matrix was therefore **not run**; component tests and CSS
inspection are not substituted for browser evidence.

| Viewport | Ukrainian/English and light/dark | Keyboard/live region and touch targets | Long labels, scroll, two interactions | Actual status |
|---|---|---|---|---|
| 320x640 | Not run | Not run | Not run | Blocked: browser runner unavailable |
| 390x844 | Not run | Not run | Not run | Blocked: browser runner unavailable |
| 768x1024 | Not run | Not run | Not run | Blocked: browser runner unavailable |
| 1280x800 | Not run | Not run | Not run | Blocked: browser runner unavailable |

Automated component evidence does pass for mutually exclusive localized states, polite live-region
attributes, programmatic heading focus, 44px-oriented authored styles, visible/focusable primary
action, and action start within one activation. These checks do not close the browser matrix.

### Privacy and architecture audit

The audit examined tracked production source, UI source, configuration, production log call sites,
tests, and tracked artifact names without printing sensitive values. It found:

- Authorization logs contain generated request identifiers and outcome codes only. Null-sub and
  duplicate warnings contain fixed text only. Automated log-capture tests exclude synthetic payload,
  subject, name, credential, upstream-detail, and permission markers.
- The UI confines `initData` to the request-scoped redemption boundary, renders neither principal
  identity field, and has automated checks excluding browser persistence and identity transport
  types. Committed configuration contains environment placeholders, not credential values.
- No tracked screenshot, analytics export, metric, trace, error-report, or support export exists for
  runtime-value inspection. The tracked image is a static logo. No telemetry or analytics facility is
  configured by the authored feature code.
- Architecture tests pass: business consumers depend on the facade, the production UI contains no
  identity REST types, `rg-logic` contains no Vaadin or identity-rest-client dependency, and contract
  DTOs have no `contractVersion` field.

This is a scoped source/configuration/test-log audit. It does not claim review of production systems
or artifacts that were not present in the repository.

### Artifact compatibility and external prerequisites

Gradle dependency insight resolved exact published releases `vg.identity:identity-api:0.0.2` and
`vg.identity:identity-rest-client:0.0.2`. The facade publication uses the exact project version
`0.1.0-SNAPSHOT`; it is suitable for development only and does not provide non-snapshot release
compatibility evidence.

Common facade contract tests pass against both implementations for their shared supported scenarios,
and identity-adapter integration tests pass for success, server failure, read timeout, safe failure
mapping, and zero application retry. Release compatibility remains blocked because published client
`0.0.2` has connect/read timeouts but no total request timeout, no configurable pre-parse raw-response
limit, and exposes permissions as a `Set`, which removes duplicate occurrences before RG can inspect
them. Consequently T001, T027, T031, and T033 remain incomplete.

No independently hosted extraction or breaking published facade change was performed by this
feature, so an extraction migration and rollback rehearsal are not applicable here and remain
explicitly deferred. Before release, publish a non-snapshot facade artifact with the intended
semantic version and consume a published identity client satisfying the capability gate. Any future
breaking facade change must use a new major version and add migration, rollback, and adapter evidence.

### Commands actually run

| Command | Result |
|---|---|
| `./gradlew :rg-logic:test` | Passed |
| `./gradlew :rg-frontend-vaadin:test` | Passed |
| `./gradlew test` | Passed |
| `./gradlew check` | Passed |
| `./gradlew :rg-logic:dependencyInsight --dependency identity-api --configuration runtimeClasspath` | Passed; resolved `0.0.2` release |
| `./gradlew :rg-frontend-vaadin:dependencyInsight --dependency identity-rest-client --configuration runtimeClasspath` | Passed; resolved `0.0.2` release |
| configured formatter/static-analysis/vulnerability/security task | Not available: no such plugin/task is configured |
| `git diff --check` | Passed after the final documentation/task update |

The latest XML reports contain 198 tests, zero failures, zero errors, and zero skipped tests.

### Final requirement reconciliation

Status meanings: **Pass** has executable or inspected evidence; **Partial** has implemented evidence
but an explicit acceptance dependency remains; **Blocked** cannot be satisfied by the currently
published identity client; **Not run** requires the missing browser runner.

| Requirement | Status | Evidence or remaining gap |
|---|---|---|
| FR-001 | Pass | One selected secure-service facade is the authorization authority. |
| FR-002 | Partial | Single version-field-free boundary and exact identity releases pass; facade remains a snapshot. |
| FR-003 | Blocked | Malformed/null/permission-limit paths pass; pre-parse raw limit and duplicate visibility are absent upstream. |
| FR-004 | Pass | Principal fidelity, opaque bounded forwarding, null-sub, false-consent, and 32 KiB boundary tests pass. |
| FR-005–FR-007 | Pass | Recognized-permission navigation plus route/method/subject checks and pre-effect denial pass. |
| FR-008–FR-011 | Pass | Stale-content removal, seven closed states, explicit retry, session replacement, and idempotency pass. |
| FR-012 | Not run | Component/CSS evidence passes, but the required viewport touch/keyboard matrix was not executed. |
| FR-013 | Partial | Outcome and null-sub warnings are privacy-safe; real duplicate warnings are blocked by upstream `Set`. |
| FR-014–FR-016 | Pass | Scope, isolated verification boundary, opaque handoff, and session-only principal handling pass. |
| FR-017 | Partial | Contract/adapter tests pass for supported outcomes; non-snapshot facade and full client compatibility are missing. |
| FR-018 | Pass | Architecture tests find no implementation or transport dependency in consumers. |
| FR-019 | Partial | Shared supported outcomes pass; raw-size and duplicate semantics cannot be proven with client `0.0.2`. |
| FR-020–FR-021 | Pass | Exactly-one selection/failure cases and implementation-independent consumers pass. |
| FR-022–FR-024 | Partial | Automated localization lifecycle passes; complete bilingual browser-flow acceptance was not run. |
| FR-025 | Partial | Defaults/custom/invalid immutable payload and permission limits pass; raw-response limit is unavailable upstream. |

| Success criterion | Status | Evidence or remaining gap |
|---|---|---|
| SC-001 | Partial | Finite 2s connect/8s read settings and safe-state test pass; no total request-timeout control exists. |
| SC-002 | Blocked | Principal and permission exact/custom boundaries pass; raw-byte and real duplicate cases are blocked upstream. |
| SC-003 | Pass | Permission-set, unknown, direct-denial, and null-sub matrices pass. |
| SC-004 | Pass | Automated primary-action test starts the focusable labeled action in one activation. |
| SC-005 | Not run | Four-viewport touch/keyboard/no-scroll browser matrix was not executed. |
| SC-006 | Pass | Scoped source/configuration/UI/test-log audit found no prohibited values outside the boundary. |
| SC-007 | Pass | Loopback timeout reaches a safe state inside 10 seconds with no retry; explicit recovery tests pass. |
| SC-008 | Pass | Repeated-key tests produce one effect and prevent cross-subject ownership. |
| SC-009 | Partial | Shared supported contract scenarios pass; unavailable client capabilities prevent full equivalence. |
| SC-010–SC-011 | Pass | Selection/startup failure and architecture/privacy dependency checks pass. |
| SC-012–SC-013 | Partial | Bundle/fallback/in-place refresh tests pass; complete visual bilingual browser flows were not run. |
| SC-014 | Pass | Null-sub plus non-empty permissions emits exactly one value-free warning in the tested result. |
| SC-015 | Pass | Default 32 KiB and custom UTF-8 exact/one-byte-over payload boundaries pass. |
| SC-016 | Partial | Payload and permission default/custom/invalid startup snapshots pass; raw limit cannot be configured. |
| SC-017–SC-018 | Blocked | Published client loses duplicate occurrences and cannot enforce raw bytes before parsing. |
