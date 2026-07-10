# Data Model: Secure Permission-Aware Telegram App

These are contract and in-memory domain models. No application-visible persistent personal-data
schema is required. Production identity fields come from identity-api; the development facade uses
the verified Telegram ID's decimal string temporarily as `sub` and creates no identity mapping.
Types marked **secure-boundary only** must never cross into the app principal, UI, logs, telemetry,
or support artifacts. Principal `sub` and `name` may exist only in the authenticated server session
or cache and are not rendered or logged.

## SecureAuthorizationFacade

Semantically versioned application port implemented by the development and identity-service
adapters. The `rg-logic` artifact version is the facade version; DTOs contain no version field.

```text
redeemAuthorizationGrant(TelegramInitDataRequest) -> AuthorizationOutcome
```

The interface does not expose Spring Security, HTTP, Vaadin, Telegram profile objects, or storage
entities.

## TelegramInitDataRequest (secure-boundary only)

| Field | Type | Rules |
|-------|------|-------|
| initData | string | Required; UTF-8; byte maximum from immutable startup configuration, default 32 KiB; never logged or persisted |

Validation additionally rejects duplicate keys, invalid percent encoding, missing hash/auth date or
user payload, future auth dates beyond clock skew, expired dates, invalid signature, bots, malformed
JSON, missing/non-integral Telegram ID, and oversized/extra-deep JSON.

The development facade applies no replay or rate-limit policy. It verifies each bounded request and
grants the complete recognized development permission catalog after successful verification. The
real identity service remains responsible for production abuse controls and permission policy.

## AuthorizationOutcome

Closed union returned by `redeemAuthorizationGrant`:

- `AUTHORIZED`: includes `AuthenticatedUserPrincipal`.
- `DENIED`: verified identity is not allowed; no identity fields.
- `INVALID_REQUEST`: malformed or unauthenticated Telegram data.
- `EXPIRED`: authentic Telegram data outside its accepted time window.
- `UNAVAILABLE`: authority cannot decide; includes a safe retry hint.
- `INCOMPATIBLE`: null, malformed, oversized, or unmappable result against the pinned artifact contract.

## AuthenticatedUserPrincipal

| Field | Type | Rules |
|-------|------|-------|
| sub | string? | Nullable opaque identity-service subject; when present, 1–128 characters and nonblank |
| name | string? | Identity-service display name as received; 1–256 characters when present |
| permissions | set of string | Immutable, unique, syntactically valid normalized entries; identity-api-specific count/length limits are enforced before construction, while development values come from the fixed internal catalog |
| consentGiven | boolean | Preserved exactly as supplied; `false` is valid |
| authenticationFlow | AuthenticationFlow | Required; `TELEGRAM` for Telegram authentication |

Validation accepts null `sub` and false consent but rejects an empty non-null subject or malformed
principal field. Unknown permission strings are retained but grant no authority. A null `sub`
suppresses all protected authority regardless of permissions; false consent does not suppress
recognized permissions when `sub` is non-null.

The principal is returned by the authorization facade and stored directly in Spring Security without
an intermediate application-principal conversion. The identity-service fields are copied as received,
and `AuthenticationFlow.TELEGRAM` records how authentication occurred. The name may exist only under
the constitution's server-side session/cache allowance and is not rendered, logged, or used for
authorization. Spring authorities are derived only from recognized permissions and are not an
independent authority. The serializable principal supports Spring/Vaadin session serialization. The
HTTP/Vaadin session lifecycle defines how long the principal remains authenticated. A principal
with null `sub` may remain authenticated but receives the no-access experience and cannot enter any
protected route or operation.

Principal lifecycle:

```text
AUTHORIZED RESULT -> SESSION PRINCIPAL INSTALLED
IDENTITY-SERVICE PERMISSION CHANGE -> CURRENT SESSION PRINCIPAL UNCHANGED
REAUTHENTICATION / AUTHENTICATED-SESSION REPLACEMENT -> NEW PRINCIPAL INSTALLED ATOMICALLY
LOGOUT / SESSION END -> PRINCIPAL DISCARDED
```

RG currently requests authentication without consent collection. Identity-api may therefore return
a provisional null-sub, false-consent principal, commonly with no permissions. RG preserves that
result, ignores all permissions for authorization when `sub` is null, and emits a privacy-safe warning
when such a result contained non-empty permissions. Consent collection is outside this feature.

## Permission

Initial recognized catalog:

| Permission | Capability |
|------------|------------|
| `home:view` | Enter the permitted landing view |
| `reports:view` | See the representative reports destination |
| `request:submit` | Execute the representative protected request action |

Contract identifiers are case-sensitive lowercase `resource:action` strings. Each segment starts
with a letter and contains only lowercase letters, digits, or hyphens. The immutable `Permissions`
registry exposes resource-grouped string constants and validates declaration syntax and uniqueness
at startup. Adding an identifier is additive; removing it or changing its semantics requires a
versioned migration. Syntactically valid but unknown identifiers remain in the principal for
identity-contract fidelity but grant no capability.

## IdentityApiAuthorizationResponse (identity boundary only)

The identity transport enforces a configurable total raw-response limit before parsing. The default
is 256 KiB, values must be positive, and no application hard ceiling exists. An oversized response
maps to `INCOMPATIBLE` before any DTO is created.

After parsing, duplicate permission occurrences are reduced to unique values and produce exactly one
privacy-safe warning without identity or permission values. The unique count is checked after
deduplication. Identity-only positive configurable defaults are 1024 unique permissions and 128
characters per permission; exceeding either maps to `INCOMPATIBLE`. The transport must preserve raw
occurrence information or an equivalent duplicate indicator long enough to support this behavior.

## FacadeImplementationSelection

| Input | Selected implementation | Rules |
|---|---|---|
| `rg.secure-service.enabled=true` | Development facade | Explicit development-only mode; unused identity client may initialize with a fake non-secret local API key and loopback/non-production base URL |
| `rg.secure-service.enabled=false` | Identity-service facade | Production-oriented path |
| Property absent | Identity-service facade | Secure default |
| Missing or multiple resulting beans | None | Application startup fails |

Facade conditions determine the sole authority even if identity-rest-client auto-configuration also
creates an unused client in development mode. A real Spring application-context test verifies
selection rather than relying only on manually imported components.

## IdentityTransportConfiguration

Owned and validated by identity-rest-client:

| Field | Type | Rules |
|---|---|---|
| base URL | URI | Required for identity mode; HTTPS outside explicitly local development |
| API key | string | Real secret required for identity mode and never logged; a clearly fake, non-secret local value is permitted with a loopback/non-production base URL when the client is unused in development mode |
| connect timeout | duration | Positive and finite; target default 2 seconds |
| total request/read timeout | duration | Positive and finite; no more than 8 seconds so RG can render failure inside the 10-second UI deadline |
| automatic retries | integer | Zero for Telegram authentication |
| raw response size | data size | Positive startup-configured value; default 256 KiB; enforced before parsing; no application hard ceiling |
| duplicate visibility | capability | Preserve raw occurrences or expose an equivalent duplicate indicator before set normalization |

Timeout or connection failure maps to `UNAVAILABLE` without upstream details. These settings are not
owned by the facade DTOs because the client owns HTTP transport.

## ContractCompatibility

| Element | Rule |
|---|---|
| Facade version | Exact semantic `rg-logic` artifact version |
| Identity version | Exact compatible identity-api and identity-rest-client artifact version |
| DTO version field | None |
| Compatible addition | Minor artifact version |
| Compatible fix | Patch artifact version |
| Breaking change | Major artifact version plus migration plan, rollback plan, and adapter contract evidence |
| Production evidence | Published non-dynamic, non-snapshot artifacts only |

`INCOMPATIBLE` represents a null, malformed, oversized, or unmappable upstream result against the
pinned artifact contract. Future transport incompatibility may map to the same domain outcome, but
external transport is outside this feature.

## PermissionAwareNavigationItem

Presentation model assembled in the frontend from an allowlisted registry.

| Field | Type | Rules |
|-------|------|-------|
| labelKey | string | Localized text key |
| icon | Vaadin icon identifier | Decorative icon; text remains visible |
| route | Vaadin view class | Fixed compile-time allowlist |
| requiredPermission | string constant | Exactly one explicit value from `Permissions.ALL` |

Only items whose permission is present are instantiated. This model controls visibility, not
authorization.

## ProtectedOperation

| Field | Type | Rules |
|-------|------|-------|
| operationId | UUID | Server-generated business correlation |
| subject | string | Required non-null authenticated principal `sub`; business association only |
| requiredPermission | string constant | Explicit value from `Permissions.ALL` |
| idempotencyKey | UUID | Required for duplicate-sensitive operation |
| state | enum | Terminal result: `COMPLETED` or `DENIED` |

State transitions:

```text
NEW -> COMPLETED
NEW + MISSING SUBJECT -> DENIED   (no idempotency entry and no effect)
EXISTING KEY + DIFFERENT SUBJECT -> DENIED
COMPLETED -> COMPLETED       (same key returns prior result; no duplicate effect)
```

## AuthorizationUiState

```text
LOADING -> PERMITTED | NO_ACCESS | DENIED | TEMPORARILY_UNAVAILABLE | INCOMPATIBLE
TEMPORARILY_UNAVAILABLE -> RETRYING -> PERMITTED | NO_ACCESS | DENIED |
                                      TEMPORARILY_UNAVAILABLE | INCOMPATIBLE
PERMITTED -> DENIED                  (session expires or authentication becomes invalid)
PERMITTED -> NO_ACCESS               (required session permission is absent)
```

Entering any non-`PERMITTED` terminal state removes protected content first. `DENIED` is used for
failed authentication/authorization; `NO_ACCESS` is for an authorized user with no recognized app
permissions or with null `sub`.

## ApplicationLocaleState (presentation/session only)

| Field | Type | Rules |
|-------|------|-------|
| selectedLocale | enum | Exactly `UKRAINIAN_UA` (`uk-UA`) or `ENGLISH` (`en`) |
| source | enum | `DEFAULT` or `MANUAL`; browser, JVM, and Telegram sources are not accepted |
| lifetime | constant | Current `VaadinSession` only |

State transitions:

```text
NEW_SESSION -> UKRAINIAN_UA(DEFAULT)
UKRAINIAN_UA -> ENGLISH(MANUAL)
ENGLISH -> UKRAINIAN_UA(MANUAL)
SESSION_ENDED -> discarded
NEXT_NEW_SESSION -> UKRAINIAN_UA(DEFAULT)
```

Null or unsupported locale input normalizes to `UKRAINIAN_UA`. The locale state is independent of
`AuthenticatedUserPrincipal` and permissions; it is not sent to the secure
service, persisted in a database, or written to cookies or browser storage. Changing it preserves
the current route and authenticated authorization state.

## Internal secure-boundary records

- `VerifiedTelegramSubject`: numeric Telegram ID and verified metadata required by the development
  verifier; lives only for request processing.
- `IdempotencyRecord`: application-service idempotency key, non-null string `sub` binding, operation ID,
  status, and prior safe outcome.

Internal records are inaccessible from frontend packages. Logs and metrics use request/operation IDs
and outcome codes only.

## Subject representation rules

- The Java contract carries the identity-service nullable `String sub` unchanged through the facade,
  authenticated principal, `AuthorityChecker`, and protected service.
- The application treats `sub` as opaque: it does not parse, reinterpret, display, or log it.
- The development facade temporarily uses the verified numeric Telegram ID's decimal string as
  `sub`; the production identity service supplies the real stable subject.
- Every protected route and service requires non-null `sub` plus its recognized permission. A null-sub
  principal may remain in the session but exposes no protected capability and is denied before state
  creation or effect execution.

## AuthorizationLimitConfiguration

| Property | Type | Scope | Default | Lifecycle |
|---|---|---|---:|---|
| `rg.secure-service.max-init-data-size` | data size | Both facade request paths; UTF-8 bytes | `32KB` (32 KiB) | Positive immutable startup value; checked before adapter processing |
| `vg.identity.rest-client.max-response-size` | data size | Identity transport raw bytes | `256KB` (256 KiB) | Positive immutable startup value; enforced before parsing |
| `rg.secure-service.identity.max-permission-count` | integer | Identity adapter only, after deduplication | `1024` | Positive immutable startup value |
| `rg.secure-service.identity.max-permission-length` | integer | Identity adapter only | `128` characters | Positive immutable startup value |

Absent settings use defaults. Zero, negative, or malformed values fail startup with a non-sensitive
error that identifies the property without echoing its value. Valid changes require restart, do not
affect in-flight requests, and have no application hard ceilings. Adding these optional properties is
backward compatible because their absence selects the documented defaults.
