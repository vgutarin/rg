# Authentication and access status

## Purpose and entry

`/login` is an anonymous route handled by `TelegramAuthView`. On attachment, an
already authenticated non-anonymous session navigates to Home. Otherwise the view
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
| Authorized, null subject | Install provisional principal and navigate to `/no-access` |
| Unavailable | Clear security context; show temporary-unavailability guidance and Retry |
| Incompatible | Clear context; show incompatibility guidance without Retry |
| Invalid, denied, expired, or invalid request construction | Clear context; show denied guidance without Retry |

Retry is an explicit user action, not polling or an automatic retry loop.
`/no-access` and `/access-denied` are authenticated status routes in the main shell,
not drawer destinations. They show `NO_ACCESS` and `DENIED` respectively.

## Shared status states

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

## Related contracts

[Foundations](foundations.md), [navigation](navigation.md), and
[identity and access](../logic/identity-access.md).

## Evidence

- [TelegramAuthView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/TelegramAuthView.java)
- [AuthorizationStatusComponent.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/AuthorizationStatusComponent.java)
- [NoAccessView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/NoAccessView.java)
- [AccessDeniedErrorView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/auth/AccessDeniedErrorView.java)
- [TelegramAuthViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/auth/TelegramAuthViewTest.java)
- [AuthorizationStatusComponentTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/auth/AuthorizationStatusComponentTest.java)
- [SecureEntryIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/SecureEntryIntegrationTest.java)
