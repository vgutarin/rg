# Identity and access

## Responsibility and language

The identity boundary authenticates Telegram data and returns an application principal.
The application decides access using declared app-wide permissions and workspace
ownership. An authenticated principal can be provisional: authentication and permission
to use a capability are distinct.

| Term | Meaning |
| --- | --- |
| Subject | Abstract identity `UniqueId`; nullable on a provisional principal |
| Principal | Subject, optional name, permission set, consent flag, and authentication flow |
| Authorization outcome | Authorized, denied, invalid request, expired, unavailable, or incompatible |
| App-wide permission | Capability meaningful without a resource identifier |
| Local permission | Declared capability interpreted against a workspace-scoped resource |

## Authentication command and trust boundary

`AuthorizationApplicationService.redeem(TelegramInitDataRequest)` validates the opaque
request before invoking `SecureAuthorizationFacade`. Blank/null init data is invalid;
UTF-8 size is bounded by `rg.secure-service.max-init-data-size` (default 32KB).
Request logs contain a generated correlation identifier and outcome, not init data.
A null facade outcome maps to incompatible.

The default `IdentitySecureAuthorizationFacade` invokes
`IdentityApplicationApi.authenticateTelegram` with the original init data and the
request's `consentToKeepPersonalData` flag. Login without a pending join intent supplies
false; the explicit Join Society consent journey supplies true.

- A null response maps to incompatible; an empty result maps to invalid request.
- Exceptions from the identity invocation map to unavailable without exposing upstream text.
- A null permission collection, malformed/overlong permission, excessive unique count,
  or invalid principal construction maps to incompatible.
- Duplicate permissions are collapsed. Limits default to 1024 distinct permissions
  and 128 characters per permission.
- A null subject with permissions is accepted as provisional but its permissions are
  cleared. The flow is `TELEGRAM`; returned consent and optional name are carried by
  the principal rather than inferred by the UI.

A principal's permission set is immutable and syntax-validated; an optional name has
a 1–256 character bound. Only an authorized outcome contains a principal.

When `rg.dev-secure-service.enabled=true`, the development adapter verifies signed
Telegram init data locally, including freshness, and uses the Telegram user identifier
as its subject. It grants the declared app-wide permissions and sets consent true.
This is distinct from the production identity-service path.

## Session and authority policies

The frontend session adapter installs the principal in Spring's security context and
persists that context to the HTTP/Vaadin session. Granted Spring authorities include
only recognized app-wide permissions for a non-null subject. A provisional subject
has none. Failure to install a context clears it.

The app-wide declarations are `workspace:owner` and `experiment:participant`.
`AuthorityChecker.hasAuthority(permission)` rejects unknown/local declarations,
missing authentication, an incompatible principal type, or a null subject.
Local declarations do not become held Spring authorities.

A resource-addressed check additionally requires the workspace-owner gate and
ownership of the resolved workspace. The local declaration is validated but is not
required in the principal's held set. Scope dispatch and failure behavior belong to
[workspace scope](workspace/README.md#authorization-scope).

A provisional Telegram principal has no subject and cannot operate on a workspace,
but can read published public events through the separate audience-policy boundary.
When that viewer chooses Join Society, the UI records a server-session-only event
intent, obtains explicit consent, and redeems Telegram authorization again with
`consentToKeepPersonalData=true`. A membership request is created only if the retry
returns a non-null subject; arbitrary route/query values never choose its event.

Labels on participant records are not identities and cannot grant authority.
`author` and `lastEditor` are auditing identifiers, not access inputs. Method security
is activated by `RgLogicConfig`.

## Functional requirements

- **FR-001**: The system MUST reject invalid or oversized Telegram init data before invoking the authorization facade.
- **FR-002**: The identity adapter MUST forward init data unchanged and preserve the request's explicit consent flag.
- **FR-003**: The system MUST map upstream invocation failures to unavailable and malformed responses to incompatible.
- **FR-004**: The identity adapter MUST clear returned permissions when the subject is null.
- **FR-005**: App-wide authority checks MUST require a recognized app-wide permission held by an authenticated application principal with a non-null subject.
- **FR-006**: Resource-addressed authority checks MUST apply the [workspace authorization contract](workspace/README.md#authorization-scope).
- **FR-007**: The session adapter MUST persist an installed security context and clear it if installation fails.

## Invariants

- **INV-001**: An outcome contains a principal if and only if its status is authorized (checked by `SecureAuthorizationContractModelTest`).
- **INV-002**: A principal's permission set is an immutable, syntax-validated snapshot (checked by `SecureAuthorizationContractModelTest`).
- **INV-003**: A provisional principal has no effective Spring authorities (checked by `ApplicationSecurityContextServiceTest`).

## Success criteria

- **SC-001**: A request exceeding the UTF-8 size limit results in zero facade calls and an invalid-request outcome (`TelegramAuthorizationRequestValidatorTest`).
- **SC-002**: A valid identity response returns the mapped principal, while an unavailable identity service yields an unavailable outcome with one upstream attempt (`IdentitySecureAuthorizationIntegrationTest`).
- **SC-003**: A principal without a subject receives zero app-wide or workspace authority grants (`AuthorityCheckerTest`).

## Interactions

[Authentication UI](../ui/authentication.md) owns retry, terminal messages, and route
outcomes. [Workspace lifecycle](workspace/lifecycle.md) resolves the caller's subject
for ownership and persistent selection. [Participant registration](workspace/participants.md)
does not query identity by phone or authenticate the registered person.

Adapter configuration uses the [shared configuration mechanism](../engineering/configuration.md).

## Evidence

- [AuthorizationApplicationService.java](../../../rg-logic/src/main/java/vg/rg/service/security/AuthorizationApplicationService.java)
- [IdentitySecureAuthorizationFacade.java](../../../rg-logic/src/main/java/vg/rg/service/security/IdentitySecureAuthorizationFacade.java)
- [IdentityAuthorizationResponseValidator.java](../../../rg-logic/src/main/java/vg/rg/service/security/IdentityAuthorizationResponseValidator.java)
- [DevSecureAuthorizationFacade.java](../../../rg-logic/src/main/java/vg/rg/service/security/DevSecureAuthorizationFacade.java)
- [ApplicationSecurityContextService.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/ApplicationSecurityContextService.java)
- [AuthorityChecker.java](../../../rg-logic/src/main/java/vg/rg/service/security/AuthorityChecker.java)
- [IdentitySecureAuthorizationFacadeContractTest.java](../../../rg-logic/src/test/java/vg/rg/service/security/IdentitySecureAuthorizationFacadeContractTest.java)
- [AuthorityCheckerTest.java](../../../rg-logic/src/test/java/vg/rg/service/security/AuthorityCheckerTest.java)
- [SecureAuthorizationContractModelTest.java](../../../rg-logic/src/test/java/vg/rg/model/security/SecureAuthorizationContractModelTest.java)
- [TelegramAuthorizationRequestValidatorTest.java](../../../rg-logic/src/test/java/vg/rg/service/security/TelegramAuthorizationRequestValidatorTest.java)
- [SecureAuthorizationObservabilityTest.java](../../../rg-logic/src/test/java/vg/rg/service/security/SecureAuthorizationObservabilityTest.java)
- [ApplicationSecurityContextServiceTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/ApplicationSecurityContextServiceTest.java)
- [IdentitySecureAuthorizationIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/IdentitySecureAuthorizationIntegrationTest.java)
