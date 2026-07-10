# Secure Authorization Facade Contract

This is the normative application-facing contract for the current feature. It is a Java facade
contract published through `rg-logic`; it is not an HTTP endpoint or an OpenAPI contract.

## Operation

```text
redeemAuthorizationGrant(TelegramInitDataRequest) -> AuthorizationOutcome
```

The operation accepts one request-scoped Telegram payload and produces one closed domain outcome.
It exposes no Spring Security, Vaadin, HTTP, identity-client, or Telegram profile type.

## Request

`TelegramInitDataRequest` contains only:

| Field | Type | Rules |
|---|---|---|
| `initData` | string | Required, nonblank, UTF-8, byte maximum from positive startup configuration (default 32 KiB), write-only in intent, never logged or persisted |

There is no request ID or `contractVersion` field. Code outside the selected facade implementation
may only transport the bounded value directly from the Telegram callback into this operation. It
must not parse, inspect, retain, display, persist, log, derive identity from, or place the value in
session state.

## Authorized Principal

An `AUTHORIZED` outcome contains exactly one `AuthenticatedUserPrincipal`:

| Field | Type | Contract |
|---|---|---|
| `sub` | nullable string | Preserved as supplied; non-null values are nonblank and 1–128 characters |
| `name` | nullable string | Preserved as supplied; non-null values are nonblank and 1–256 characters |
| `permissions` | set of string | Required, immutable, unique, and follows lowercase `resource:action` syntax; identity-only configurable limits are applied before construction |
| `consentGiven` | boolean | Preserved as supplied; either value is valid |
| `authenticationFlow` | enum | Required; `TELEGRAM` for this operation |

Null `sub` and false consent do not invalidate an otherwise valid authorized principal. Null `sub`
causes every permission to be ignored for authorization and exposes no protected capability. False
consent does not suppress permissions when `sub` is non-null. Unknown but syntactically valid
permissions remain in the principal for fidelity but grant no application capability.

## Outcomes

| Outcome | Meaning | Principal |
|---|---|---|
| `AUTHORIZED` | Structurally valid authenticated principal returned | Required |
| `DENIED` | Authority explicitly denies access | Absent |
| `INVALID_REQUEST` | Input cannot establish authentication | Absent |
| `EXPIRED` | Authenticated Telegram data is outside the accepted time window | Absent |
| `UNAVAILABLE` | Authority or transport cannot currently decide | Absent |
| `INCOMPATIBLE` | Result is null, malformed, oversized, or cannot be mapped to the pinned contract | Absent |

Messages, exception text, response bodies, identity values, and Telegram payloads never cross the
facade. A bounded safe retry hint may accompany `UNAVAILABLE`; it contains no upstream detail.

## Adapter Mapping

| Scenario | Development facade | Identity-service facade |
|---|---|---|
| Valid established principal | `AUTHORIZED` | `AUTHORIZED`, identity fields preserved plus `TELEGRAM` |
| Valid provisional principal | Not normally produced | `AUTHORIZED`, including null `sub` and false consent |
| Invalid or unauthenticated input | `INVALID_REQUEST` | Empty identity-api result maps to `INVALID_REQUEST` |
| Expired Telegram input | `EXPIRED` | Not separately distinguishable by the current identity-api result |
| Explicit denial | Not separately produced by current development policy | Not separately distinguishable by the current identity-api result |
| Null/malformed successful result | `INCOMPATIBLE` | `INCOMPATIBLE` |
| Raw response exceeds configured total limit | Not applicable | `INCOMPATIBLE` before parsing |
| Duplicate permissions | Not applicable; fixed catalog | Deduplicate, warn once without values, then validate unique count |
| Permission count/length exceeds configured identity limit | Not applicable; fixed catalog | `INCOMPATIBLE` |
| Timeout/client/runtime failure | Not applicable to local verification | `UNAVAILABLE`, upstream detail discarded |

Common contract tests cover shared invariants and supported common outcomes. Adapter-specific tests
cover distinctions that only one upstream can represent; tests must not fabricate equivalence for
outcomes identity-api cannot currently distinguish.

## Implementation Selection

| Configuration | Required bean |
|---|---|
| `rg.secure-service.enabled=true` | Development facade only |
| `rg.secure-service.enabled=false` | Identity-service facade only |
| Property missing | Identity-service facade only |

Missing or multiple facade beans fail application startup. In development mode the identity client
may still be auto-configured with a clearly fake, non-secret local API key. The selected development
facade must never invoke it, and the unused client must target a loopback/non-production base URL.

## Authorization After Redemption

- The authenticated server session owns authorization lifetime.
- `AuthorityChecker.hasAuthority(permission)` requires the current typed principal, non-null `sub`,
  and the recognized permission; it does not require consent.
- Missing `sub` yields no protected authority and a controlled denial before state creation or effect
  execution. Non-empty permissions with null `sub` produce one privacy-safe value-free warning.
- Identity-service permission changes do not mutate an existing session. Reauthentication or
  authenticated-session replacement atomically installs the refreshed principal.

## Resilience and Privacy

- Identity-rest-client owns finite connect/read/request timeouts through validated
  `vg.identity.rest-client.*` configuration.
- Identity-rest-client enforces a positive configurable total raw-response limit before parsing,
  default 256 KiB with no application hard ceiling, and preserves duplicate occurrence information
  or an equivalent indicator.
- RG deduplicates identity permissions with one value-free warning, then evaluates positive
  configurable unique-count and length limits, defaults 1024 and 128. These do not apply to the
  development facade's fixed catalog.
- RG performs no automatic Telegram-authentication retry. Explicit user retry is allowed after a
  definite failure.
- Timeout and transport failure map to `UNAVAILABLE`; malformed successful results map to
  `INCOMPATIBLE`.
- Logs, metrics, traces, errors, screenshots, and support artifacts contain no `initData`, `sub`,
  name, Telegram identity, API key, or upstream response detail.

## Limit Configuration

| Property | Type | Default | Enforcement point |
|---|---|---:|---|
| `rg.secure-service.max-init-data-size` | Spring `DataSize` | `32KB` | UTF-8 bytes, before either facade adapter processes the request |
| `vg.identity.rest-client.max-response-size` | Spring `DataSize` | `256KB` | Raw response bytes, before identity DTO parsing |
| `rg.secure-service.identity.max-permission-count` | positive integer | `1024` | Unique identity permissions after deduplication |
| `rg.secure-service.identity.max-permission-length` | positive integer | `128` | Each identity permission before principal construction |

The four limits are immutable startup settings. Missing values use defaults. Zero, negative, or
malformed values fail startup with a stable non-sensitive error that identifies the property without
echoing its value. Valid changes require restart and do not alter in-flight requests. No application
hard ceilings are imposed. These optional properties are additive to the existing configuration
contract; existing deployments retain the defaults until they opt into custom values.

## Versioning

- `rg-logic`, identity-api, and identity-rest-client use exact semantic artifact versions.
- DTOs contain no runtime contract-version field.
- Compatible additions increment minor versions; fixes increment patch versions.
- Breaking changes increment major versions and require migration, rollback, and adapter contract
  evidence.
- Snapshot or dynamic versions are development-only and do not satisfy release compatibility
  evidence.
