# Authentication and access status

## Purpose and actors

Telegram viewers establish an application session; authenticated viewers receive
localized access-status feedback when a capability is unavailable to them.

## Routes and entry conditions

`/login` is an anonymous route handled by `TelegramAuthView`. On attachment, an
already authenticated non-anonymous session navigates to Home unless a pending Join
Society intent requires a provisional principal to obtain a subject. Otherwise the view
requests the opaque `window.Telegram.WebApp.initData` string from the browser; absent
Telegram data becomes an empty string. Identity validation is owned by
[identity and access](../logic/identity-access.md).

## Interactions and outcomes

Each authorization attempt accepts one browser callback. Further callbacks for that
attempt are ignored. Retry resets that callback guard and reads init data again;
an in-flight request cannot be started twice.

| Outcome | Session and UI result |
| --- | --- |
| Authorized, non-null subject | Install principal and navigate to Home |
| Authorized, null subject | Install provisional principal and navigate to Home, where published public events remain available |
| Unavailable | Clear security context; show temporary-unavailability guidance and Retry |
| Incompatible | Clear context; show incompatibility guidance without Retry |
| Invalid, denied, expired, or invalid request construction | Clear context; show denied guidance without Retry |

Retry is an explicit user action, not polling or an automatic retry loop.
Consent upgrades retain their server-session join intent when authorization fails or
returns another provisional principal. An explicit retry forwards consent again.
Home continuation follows the [navigation contract](navigation.md#home).
`/no-access` and `/access-denied` are authenticated status routes in the main shell,
not drawer destinations. They show `NO_ACCESS` and `DENIED` respectively.

## Functional requirements

- **FR-001**: Login MUST redeem at most one browser callback per attempt; an explicit Retry MUST start a fresh attempt.
- **FR-002**: An authorized result MUST install its principal and navigate to Home, including when its subject is null.
- **FR-003**: Unavailable authorization MUST clear the context and offer explicit Retry; incompatible or denied outcomes MUST clear the context without offering Retry.
- **FR-004**: A pending server-session Join Society intent MUST permit reauthorization of a provisional session and supply the consent flag on that request.
- **FR-005**: Locale changes MUST refresh status text and page title without replaying terminal-heading focus.

## States and failures

`AuthorizationStatusComponent` supports `LOADING`, `PERMITTED`, `NO_ACCESS`,
`DENIED`, `TEMPORARILY_UNAVAILABLE`, `INCOMPATIBLE`, and `RETRYING`.

- Loading and retrying show indeterminate progress.
- Retry is visible for temporary unavailability and retrying, and enabled only for
  temporary unavailability.
- Protected content is cleared on state changes and visible only when supplied for
  the permitted state.
- The status container is a polite, atomic live region. Terminal status changes
  focus the heading without scrolling. Locale changes refresh text without replaying
  that focus transition.
- Login uses a centered full-size layout. Messages, progress labels, retry labels,
  and login page title are localized; raw init data and upstream errors are not shown.

## Invariants

- **INV-001**: Protected status content is cleared on every state change and is visible only in the permitted state when content is supplied (checked by `AuthorizationStatusComponentTest`).
- **INV-002**: Retry is enabled only in the temporarily-unavailable state (checked by `AuthorizationStatusComponentTest`).
- **INV-003**: A repeated callback for the same attempt causes no second redemption (checked by `TelegramAuthViewTest`).

## Success criteria

- **SC-001**: Both established and provisional authorization results install a session and navigate to Home (`TelegramAuthViewTest`).
- **SC-002**: Repeated activation of Retry while a request is in flight starts exactly one browser authorization request (`TelegramAuthViewTest`).
- **SC-003**: All seven shared status states render localized text and polite, atomic status announcements (`AuthorizationStatusComponentTest`).
- **SC-004**: Pending-intent login entry and explicit retry forward consent; an established subject resumes the selected session's event once while failed/provisional upgrades retain it (`TelegramAuthConsentTest`, `JoinSocietyResumeIntegrationTest`).

## Related contracts

[Foundations](foundations.md), [navigation](navigation.md), and
[identity and access](../logic/identity-access.md).

## Evidence

- [TelegramAuthView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/TelegramAuthView.java)
- [AuthorizationStatusComponent.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/AuthorizationStatusComponent.java)
- [NoAccessView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/NoAccessView.java)
- [AccessDeniedErrorView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/AccessDeniedErrorView.java)
- [TelegramAuthViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/auth/TelegramAuthViewTest.java)
- [TelegramAuthConsentTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/auth/TelegramAuthConsentTest.java)
- [JoinSocietyResumeIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/JoinSocietyResumeIntegrationTest.java)
- [AuthorizationStatusComponentTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/auth/AuthorizationStatusComponentTest.java)
- [SecureEntryIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/SecureEntryIntegrationTest.java)
