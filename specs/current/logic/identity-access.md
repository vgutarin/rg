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
`IdentityApplicationApi.authenticateTelegram` with the original init data and
`consentToKeepPersonalData = false`.

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

Labels on participant records are not identities and cannot grant authority.
`author` and `lastEditor` are auditing identifiers, not access inputs. Method security
is activated by `RgLogicConfig`.

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
- [IdentitySecureAuthorizationIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/IdentitySecureAuthorizationIntegrationTest.java)
