# Implementation Plan: Secure Permission-Aware Telegram App

**Branch**: `001-secure-telegram-app` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-secure-telegram-app/spec.md`

## Summary

Keep one transport-neutral `SecureAuthorizationFacade` in `rg-logic` with one operation,
`redeemAuthorizationGrant(TelegramInitDataRequest)`. The application provides two Spring-managed
implementations: an explicitly enabled development implementation that verifies Telegram init data
locally, and a default identity-service adapter that delegates through identity-api. UI and business
consumers depend only on the facade. Actual independently hosted extraction, migration execution,
rollback rehearsal, and extracted-service equivalence testing are deferred to a separate feature.

The facade returns a serializable `AuthenticatedUserPrincipal` containing identity-api's nullable
opaque `sub`, optional display name, permissions, consent flag, and
`AuthenticationFlow.TELEGRAM`. A null `sub` and false consent remain structurally valid, but every
protected route and operation requires both a non-null `sub` and its recognized permission. A
null-sub identity result may establish a session, ignores every returned permission for authorization,
shows the no-access experience, and produces a privacy-safe warning when permissions were non-empty.
Permission changes take effect only when reauthentication or authenticated-session replacement
installs a new principal.

Retain the mobile-first Vaadin 25.2 Aura experience, deterministic Ukrainian/English localization,
permission-filtered navigation, server-enforced route and method authorization, safe recovery
states, and business-level idempotency. Each allowed primary action must be visible, clearly
labeled, keyboard-focusable, and startable from the landing experience within two user
interactions.

## Technical Context

**Language/Version**: Java 21; minimal JavaScript only for the Telegram Web App callback; CSS using
Vaadin Aura tokens

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security, Vaadin 25.2.2 Flow/Aura, Jackson 3,
identity-api, identity-rest-client, and the existing Gradle modules. No new framework or service is
introduced. Development builds may use snapshot project/identity artifacts; production evidence
requires exact, published, non-dynamic, non-snapshot versions.

**Storage**: No durable application-side personal-data storage. The authenticated principal and
optional display name live only in the server-side authenticated session. The development facade
performs no identity mapping or permission persistence and temporarily uses the verified Telegram
numeric ID's decimal string as `sub`. Business idempotency state remains in the application service
layer and may be created only when a non-null subject exists. Locale remains presentation-only
`VaadinSession` state and is never written to cookies, browser storage, databases, the principal,
or secure-service requests.

**Integration**: `IdentitySecureAuthorizationFacade` maps identity-api output into the application
contract. The identity-rest-client owns HTTP transport, service authentication, base URL, finite
connect/read/request timeouts, the configurable raw-response byte limit enforced before parsing,
and duplicate-permission visibility. RG applies configurable identity-only unique-permission-count
and permission-length validation after duplicate removal. RG performs no automatic retry of Telegram
authentication; the UI may offer an explicit retry after a definite safe failure. The client may
remain auto-configured when development mode is explicitly selected. Local configuration may
supply an explicitly fake, non-secret API key paired with a loopback/non-production base URL because
the development facade never calls that client.

**Testing**: JUnit 5, Spring Boot Test, Mockito with `MockitoExtension`, common facade-contract tests
for both implementations' shared semantics, implementation-specific outcome tests, identity client
integration tests for timeouts, pre-parse response sizing, duplicate detection, and configurable
permission validation, real Spring component-selection and invalid-configuration tests, principal
serialization and nullable-field tests, route/method authorization tests, null-sub global-denial
tests, idempotency tests, privacy/warning checks, localization tests, locale/session integration
tests, and manual responsive/keyboard validation.

**Target Platform**: Telegram Mini App webview and modern desktop browsers; JVM server deployment

**Project Type**: Two-module Java web application: `rg-logic` and `rg-frontend-vaadin`

**Performance Goals**: End-to-end landing latency is not an acceptance measurement for this feature.
Dependency failure reaches a safe state within ten seconds. Identity transport uses validated finite
timeouts shorter than the ten-second UI deadline: target defaults are two seconds for connection
establishment and at most eight seconds for the total request/read deadline. Integration tests verify
the configured timeouts and safe-state deadline.

**Constraints**:

- Raw Telegram `initData` uses a positive startup-validated configurable limit, default 32 KiB with
  no application hard ceiling; it is request-scoped, opaque outside the secure facade, and never
  retained, displayed, persisted, or logged.
- `rg.secure-service.enabled=true` selects only `DevSecureAuthorizationFacade`; false or missing
  selects only `IdentitySecureAuthorizationFacade`. Missing or ambiguous facade wiring fails startup.
- Identity transport must have finite timeouts, no implicit retry, a positive configurable raw-response
  limit defaulting to 256 KiB and enforced before parsing, and duplicate-permission visibility. Until
  a published identity-rest-client supports all four, production readiness fails.
- External responses are untrusted: null, malformed, oversized, or unmappable results fail closed
  as `INCOMPATIBLE`; transport/runtime failures become non-sensitive `UNAVAILABLE` outcomes.
- Every protected capability requires a non-null authenticated `sub` plus a recognized permission.
  Null-sub results ignore all permissions; false consent does not suppress permissions when `sub`
  is non-null.
- Identity-api results deduplicate permissions with one privacy-safe warning, then apply positive
  configurable limits to unique count and individual length, defaulting to 1024 and 128. These limits
  have no application hard ceilings and do not apply to the development facade's fixed catalog.
- All configurable limits are immutable startup snapshots. Missing values use defaults; zero,
  negative, or malformed values fail startup without exposing the configured value.
- The current session principal remains authoritative until reauthentication or authenticated-session
  replacement. Browser reload and locale changes are not permission refresh mechanisms.
- Contract compatibility uses pinned semantic build-artifact versions, not request/response
  `contractVersion` fields. Breaking changes require a major version plus migration and rollback plans.
- Development secrets live only in ignored local configuration or approved runtime secret sources;
  committed configuration contains names/placeholders only.
- Supported locales are exactly `uk-UA` and `en`; Ukrainian is the deterministic default and
  terminal fallback. Locale changes rerender attached components in place without `Page.reload()`.
- Mobile-first UI starts at 320px, has 44px-equivalent touch targets, visible focus, no horizontal
  page scroll, semantic state announcements, and no identity rendering.

**Scale/Scope**: One facade operation, two selectable implementations, three recognized permissions,
two protected destinations, one idempotent action, four startup-configured input limits, one
permission-aware shell, seven authorization UI states, and two locales. Permission administration,
personal-data screens,
alternate authentication, consent collection, external transport extraction, and migration/rollback
execution are excluded.

## Constitution Check

*GATE: Passed for the planned design. Implementation/release remains blocked until every required
verification below passes; no constitutional exception is assumed.*

| Principle / gate | Design evidence and required verification | Result |
|---|---|---|
| I. Personal-data prohibition | Bounded `initData` crosses only into the facade. `sub` and `name` remain server-session-only and never render or enter telemetry. False consent is preserved but does not authorize personal-data persistence. | PASS |
| II. Secure-service trust boundary | Consumers use only `SecureAuthorizationFacade`; the development verifier or identity-api is authoritative according to exactly-one configured implementation. Secrets remain runtime-only. | PASS |
| III. Delegated Telegram authorization | Only a facade implementation authenticates Telegram data. Every protected capability requires the secure-service-supplied non-null subject plus its recognized permission; null-sub sessions expose no protected capability. | PASS |
| IV. Resilient middleware | Identity transport provides finite timeouts, no implicit retry, a pre-parse raw-response bound, duplicate visibility, safe failure mapping, and required contract coverage. Consuming a client with those controls is a hard prerequisite. | PASS |
| V. Mobile-first accessible internationalized UX | Aura mobile-first shell, semantic status states, two complete locale bundles, deterministic Ukrainian fallback, keyboard/touch behavior, and the two-interaction primary-action rule are testable. | PASS |
| VI. Module ownership | Business/security behavior remains in `rg-logic`; Vaadin presentation/routing remains in `rg-frontend-vaadin`. | PASS |
| Mandatory test coverage | Every changed production path requires unit plus integration/contract coverage, including identity-client timeout and real component selection. | PASS |
| Simplicity | Reuses two modules and two adapters; no REST modules, decision TTL, capability facade method, replay/rate-limit abstraction, or new persistence is introduced. | PASS |

### Post-design re-check

Phase 1 keeps the application facade transport-neutral, models null-sub principals explicitly,
and denies every protected capability without a subject. The normative facade contract uses artifact
semantic versioning and removes the obsolete runtime OpenAPI draft from current acceptance.
Identity transport timeout, pre-parse response-limit, and duplicate-visibility support remain explicit
prerequisites, not silently accepted gaps.
Identity client auto-configuration in development is accepted with a clearly fake, non-secret local
API key because the client is unused. Locale and UI contracts remain independent of identity
transport. No constitution waiver is required; timeout and real-context verification must pass
before feature completion.

## Project Structure

### Documentation

```text
specs/001-secure-telegram-app/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── secure-authorization-facade.md
│   └── permission-aware-ui.md
└── tasks.md
```

### Source Code in Scope

```text
rg-logic/src/main/java/vg/rg/security/
├── SecureAuthorizationFacade.java
├── AuthorizationApplicationService.java
├── SecureAuthorizationLimitsProperties.java
├── TelegramAuthorizationRequestValidator.java
├── AuthorityChecker.java
├── model/
│   ├── AuthenticatedUserPrincipal.java
│   ├── AuthenticationFlow.java
│   ├── AuthorizationOutcome.java
│   ├── Permissions.java
│   └── TelegramInitDataRequest.java
├── dev/
│   ├── DevSecureAuthorizationFacade.java
│   ├── DevSecureServiceProperties.java
│   └── TelegramInitDataVerifier.java
└── identity/
    ├── IdentitySecureAuthorizationFacade.java
    ├── IdentityAuthorizationLimitsProperties.java
    └── IdentityAuthorizationResponseValidator.java

rg-logic/src/main/java/vg/rg/service/
├── ProtectedActionService.java
└── ProtectedActionServiceImpl.java

rg-logic/src/test/java/vg/rg/
├── contract/                         # common facade contract
├── security/                         # model, authority, wiring, observability
├── security/dev/                     # verifier and development adapter
├── security/identity/                # identity mapping and supported outcomes
└── service/                          # authorization, subject denial, idempotency

rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/
├── MainView.java
├── security/ApplicationSecurityContextService.java
├── service/LocalizationService.java
├── telegram/TelegramAuthView.java
└── view/                              # landing, reports, denial, status components

rg-frontend-vaadin/src/main/resources/
├── application.properties
├── messages.properties
├── messages_en.properties
└── META-INF/resources/                # Aura styles

rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/
├── security/                          # session and architecture coverage
├── service/                           # locale and bundle coverage
├── telegram/                          # secure-entry states
└── view/                              # permission, locale, state, interaction coverage
```

Unrelated `TemplateModel` and `TemplateService` files are not part of this feature plan. No
`rg-api`, REST service/client, or standalone integration-test module is introduced.

## Facade, Versioning, and Selection Design

`SecureAuthorizationFacade` exposes only:

```text
redeemAuthorizationGrant(TelegramInitDataRequest) -> AuthorizationOutcome
```

The request contains only bounded opaque `initData`; its positive startup configuration defaults to
32 KiB and has no application hard ceiling. `TelegramAuthorizationRequestValidator`, injected with
the immutable startup settings, measures the UTF-8 byte length before either adapter parses or sends
the value. The request has no request ID or contract-version field.
The closed outcome set is `AUTHORIZED`, `DENIED`, `INVALID_REQUEST`, `EXPIRED`, `UNAVAILABLE`, and
`INCOMPATIBLE`. Common contract tests cover invariants shared by both adapters. Adapter-specific
tests cover outcomes the upstream API can actually distinguish; identity-api currently maps an empty
result to `INVALID_REQUEST`, null/malformed data to `INCOMPATIBLE`, runtime/timeout failure to
`UNAVAILABLE`, and a valid result to `AUTHORIZED`.

`AuthenticatedUserPrincipal` preserves `sub`, `name`, normalized unique permissions, and
`consentGiven` and adds `AuthenticationFlow.TELEGRAM`. `sub` is nullable; a non-null value must be
1–128 characters. False consent is valid. Common principal validation retains immutable syntactically
valid permissions but does not impose identity-api-specific count or length defaults. The identity
adapter deduplicates occurrences with one value-free warning, then applies startup-configured unique
count and length limits (defaults 1024 and 128). Unknown valid values remain in the principal for
fidelity but never become Spring authorities or recognized capabilities.

The `rg-logic` publication and identity-api/rest-client dependencies use exact semantic artifact
versions. Compatible additive changes increment minor versions; fixes increment patch versions;
breaking changes increment major versions and require migration, rollback, and adapter compatibility
evidence. Snapshot versions are development-only and cannot satisfy release compatibility evidence.

Spring component selection is intentionally asymmetric:

- `rg.secure-service.enabled=true`: development facade only.
- `rg.secure-service.enabled=false` or missing: identity facade only.
- Identity-rest-client auto-configuration may create its client in development mode. Supply a clearly
  fake, non-secret local API key paired with a loopback/non-production base URL if validation requires
  one; the development facade remains the sole selected facade and never calls the identity client.
- Real application-context tests—not only manually imported components with mocked dependencies—prove
  exactly-one selection and validate missing/conflicting configuration behavior.

## Authorization and Session Design

`ApplicationSecurityContextService` installs the complete typed principal in Spring Security.
`AuthorityChecker.hasAuthority(permission)` requires authenticated-principal presence, non-null
`sub`, recognized permission syntax/catalog membership, and the principal's permission set. It does
not require consent.

A null-sub principal may remain authenticated but receives no Spring authorities, protected
navigation, or protected actions. If identity-api supplied non-empty permissions with null `sub`, the
adapter emits one privacy-safe warning without identity or permission values. Service operations also
require `AuthorityChecker.currentSubject()` at their business boundary and return a stable denied
result before idempotency state or effects when absent. This is a normal authorization denial, not an
internal error.

The authenticated session defines authorization lifetime. Existing permissions remain effective
until reauthentication or authenticated-session replacement atomically installs a new principal.
No decision TTL, per-operation identity call, browser reload, or locale switch refreshes permissions.

## Identity Transport and Failure Design

The identity-rest-client owns HTTP details and must expose validated, bounded connect/read/request
timeouts plus a pre-parse total-response byte limit. RG supplies values through safe runtime
configuration; timeout targets are two seconds for connection and at most eight seconds overall, and
the response-size default is 256 KiB with no application hard ceiling. The client must also preserve
raw permission occurrence information or expose an equivalent duplicate indicator before mapping to
a set, so RG can emit the required warning.
Authentication has no automatic retry; users explicitly retry only after a definite failure. Timeout,
connection failure, or other client runtime failure maps to `UNAVAILABLE` without upstream detail.
Raw responses over the configured total limit are rejected before parsing as `INCOMPATIBLE`. After
parsing, RG deduplicates permissions, warns once when duplicates existed, applies configurable
identity-only unique-count and length limits, and maps exceeded or malformed results to `INCOMPATIBLE`.

The current RG request sends `consentToKeepPersonalData=false`. Existing identity users may return
an established subject and permissions; new users may return a provisional principal with null
subject, false consent, and no permissions. Consent collection is a separate, explicitly out-of-scope
flow. RG preserves the result rather than inventing consent or identity.

The additive configuration contract is:

| Property | Type | Default | Owner |
|---|---|---:|---|
| `rg.secure-service.max-init-data-size` | Spring `DataSize` | `32KB` | RG shared secure boundary |
| `vg.identity.rest-client.max-response-size` | Spring `DataSize` | `256KB` | identity-rest-client transport |
| `rg.secure-service.identity.max-permission-count` | positive integer | `1024` | RG identity adapter |
| `rg.secure-service.identity.max-permission-length` | positive integer | `128` | RG identity adapter |

Sizes are binary KiB defaults expressed using Spring's `KB` data-size syntax and are enforced as
UTF-8/raw response bytes, respectively. Missing properties retain the listed defaults for backward
compatibility. Binding or validation failures stop startup with a stable non-sensitive message that
names the property but does not echo its supplied value.

## UI and Localization Design

- Use mobile-first `AppLayout`, overlay drawer on narrow screens, `SideNav`, semantic status cards,
  44px-equivalent controls, visible focus, and no identity/profile rendering.
- Filter navigation from recognized permissions, but independently check every route and service
  operation against the current session principal.
- For every permission set with an allowed primary action, make it visible, clearly labeled,
  keyboard-focusable, and startable within at most two user activations. Passive page load and
  scrolling do not count as interactions.
- Support exactly Ukrainian (`uk-UA`) and English (`en`). Ukrainian is the fresh-session default and
  terminal fallback; selection lives only in `VaadinSession`.
- Locale selection uses Vaadin locale propagation and `LocaleChangeObserver`-style in-place rerendering.
  It preserves route/query, authentication, principal, permissions, and never calls the facade solely
  because language changed. `Page.reload()` is not the locale-switch mechanism.

## Implementation Sequence

1. Align the facade contract, principal model, and shared tests with null-sub global denial, false
   consent, adapter-owned configurable permission limits, and semantic artifact versioning.
2. Split common facade conformance from adapter-specific outcomes; run common semantics against both
   development and identity implementations.
3. Make every protected route and operation require non-null `sub`; ensure null-sub principals receive
   no authorities or protected content and services deny before state or effects.
4. Add session-replacement tests showing old permissions remain before replacement and new permissions
   apply immediately afterward; prove locale change does not reauthenticate.
5. Provide/consume a published identity-rest-client version with finite validated timeouts, no
   automatic retry, a configurable pre-parse response limit, and duplicate-permission visibility;
   pin exact releases. Permit a fake non-secret local API key solely for unused development-mode
   auto-configuration.
6. Add the four named startup-bound properties for 32 KiB init data, 256 KiB raw identity response,
   1024 unique identity permissions, and 128-character identity permissions. Validate init data once
   at the shared secure boundary before either adapter processes it. Add real-context tests for
   defaults, custom values, invalid startup configuration, selection, timeout, exact byte boundaries,
   oversize responses, duplicates, and safe failure mapping.
7. Remove any dormant principal-logging code and audit committed configuration, logs, metrics,
   traces, errors, and support artifacts without exposing credential or identity values.
8. Add deterministic two-interaction primary-action acceptance coverage alongside the existing
   responsive, keyboard, localization, and permission matrix.
9. Run focused module tests, the full build, dependency/security checks, facade artifact compatibility
   checks, and `git diff --check`; record evidence in `quickstart.md`.

## Complexity Tracking

No constitutional exception is required. One small identity-response policy component is justified
because count and length limits are adapter-specific and configurable while the shared principal must
also support the development catalog. The external prerequisite is a published identity-rest-client
that provides finite timeouts, pre-parse total-response limiting, and duplicate visibility. RG cannot
safely retrofit these after the client has read and normalized the response. If that release is not
available, production identity integration remains blocked rather than accepting unbounded or lossy
input handling.
