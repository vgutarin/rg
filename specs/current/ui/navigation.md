# Home and navigation

The authenticated shell exposes Home and capability destinations. Authentication and
access-status interactions are defined in [authentication](authentication.md).

## Purpose and actors

Authenticated viewers discover visible events on Home; permission holders navigate
to experiments and owner-managed workspace content.

## Routes and entry conditions

Home is `/` inside `MainView` and requires an application principal. Destinations
and their gates are listed below; entering a route also applies its own view/service guards.

## Interactions and outcomes

### Top-level destinations

The drawer presents the ordered navigation provider as Android-style tiles in three columns: Home, Dates &
times, Locations, Participants, and Events. Each full tile is an icon-and-label route link and retains the
compact tile dimensions. Home is always shown. Dates & times requires `experiment:participant` and
does not resolve or provision a workspace. Locations, Participants, and Events are included only when the
caller holds `workspace:owner`, has an active workspace, and passes the corresponding scoped list check.
The provider resolves that workspace once per render before evaluating those three entries.

The workspace-management route, login, and access-status routes remain outside user navigation.

### Telegram native back navigation

The Telegram WebApp `BackButton` bridge is loaded with the authenticated shell but starts observing routes
only after `MainView` completes a Telegram-authenticated navigation. Its first route is that completed
route, with zero known transitions, so login and other pre-authentication routes cannot become app
history. Later `MainView` navigations leave that history intact. The bridge stays inert before this
boundary and outside Telegram, where the WebApp SDK is unavailable.

The first BackButton update after startup uses that server-provided baseline without re-reading the
browser URL. This avoids counting a login-to-Home client redirect whose URL update arrives after the
server's completed-navigation command.

Once the browser URL matches the server-confirmed authenticated route, the bridge adds a duplicate of that
entry as a safety boundary. This prevents its own browser-back operation from reaching the preceding login
entry, even if Vaadin has replaced rather than pushed a route during the authentication redirect.

It never traverses from the Mini App into Telegram's host history. From the first in-app transition onward it
returns by stepping the browser back through the exact route transitions observed during the current Mini App
session, revisits included: `Locations → Home → Locations` walks back `Locations → Home → Locations →
landing`. The button is shown once the session has at least one known in-app transition, and is hidden only
at the landing baseline, where a back step would otherwise reach the preceding login entry.

### Home

The home page shows a localized welcome message and an audience-filtered event feed.
Rows use expandable details and disclose event title, schedule, type, capacity,
and location name; the feed exposes no roster, contact data,
location coordinates, or workspace-management action. The server-side audience projection
also carries safe location details for reuse, but Home does not render them.

A non-member sees Join Society for a visible event, while a pending request displays
its pending state. A provisional viewer can browse published public events. Choosing
Join Society opens explicit consent and re-runs Telegram authorization; only a newly
obtained subject resumes the server-session-held join intent. Declining consent leaves
the viewer on Home without a request. Navigation is available from the drawer tile panel.

Once an established subject reaches Home, Home consumes the selected event's intent
from the current server session before submitting it to the membership service.
Repeated rendering does not replay a consumed intent, including after a service
refusal. The [membership service](../logic/workspace/participants.md#membership-requests)
rechecks the event's audience eligibility at submission time.

The shell resolves nested route titles from the active navigation chain and retranslates
the resolved title on locale change. Workspace selector behavior belongs to
[workspaces](workspaces.md); shared presentation belongs to [foundations](foundations.md).

## Functional requirements

- **FR-001**: The drawer MUST display only destinations admitted by the ordered navigation provider.
- **FR-002**: Rendering workspace destinations MUST resolve the active workspace once and check each destination's scoped list permission.
- **FR-003**: Home MUST request the first 100 audience-visible events and render only the display fields listed in [Home](#home).
- **FR-004**: A provisional viewer choosing Join Society MUST explicitly confirm consent before the UI stores an event intent and navigates to login.
- **FR-005**: The shell MUST resolve the leaf route's title from the active navigation chain and retranslate it on locale change.
- **FR-006**: Telegram native Back MUST follow observed authenticated route transitions and stop at the session's landing baseline.

## States and failures

An empty Home feed shows a localized empty state. A pending membership request
replaces Join Society with a pending message; a bound member has no Join action.
Join failures use localized feedback. The BackButton bridge remains inert before
its authenticated baseline and when the Telegram SDK is absent.

## Presentation and platform constraints

The drawer uses three-column route tiles. [Foundations](foundations.md) own shared
layout and localization. Native Back behavior depends on the Telegram WebApp SDK.

## Invariants

- **INV-001**: Without workspace ownership, constructing navigation performs no active-workspace lookup (checked by `ContentNavigationProviderTest`).
- **INV-002**: A provisional session has no effective permission-based workspace destinations (checked by `MainViewTest`).
- **INV-003**: The native Back bridge does not traverse across its authenticated landing boundary (checked by `telegram-navigation.test.mjs`).

## Success criteria

- **SC-001**: A fully permitted principal receives Home, Dates & times, Locations, Participants, and Events in that order (`ContentNavigationProviderTest`).
- **SC-002**: A nested workspace route displays its own translated page title, including after switching locale (`MainViewTest`).
- **SC-003**: An audience event with a nested location name renders that name on Home (`LandingViewTest`).
- **SC-004**: Cancelling consent creates no intent; confirmation records the selected event only after consent is checked and navigates to login (`LandingJoinSocietyTest`).
- **SC-005**: An established subject resumes the selected session's event once; provisional authorization retains the intent (`JoinSocietyResumeIntegrationTest`).

## Related contracts

- [Authentication](authentication.md), [workspaces](workspaces.md), and [shared interactions](shared-interactions.md).
- [Resource publication](../logic/resource-publication.md) and [membership requests](../logic/workspace/participants.md#membership-requests).

## Evidence

- [MainView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/MainView.java)
- [ContentNavigationProvider.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/ContentNavigationProvider.java)
- [LandingView.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/home/LandingView.java)
- [LandingViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/home/LandingViewTest.java)
- [LandingJoinSocietyTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/home/LandingJoinSocietyTest.java)
- [JoinSocietyResumeIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/security/JoinSocietyResumeIntegrationTest.java)
- [telegram-navigation.ts](../../../rg-frontend-vaadin/src/main/frontend/ts/telegram-navigation.ts)
- [MainViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/MainViewTest.java)
- [ContentNavigationProviderTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/ContentNavigationProviderTest.java)
- [JoinSocietyIntent.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view/home/JoinSocietyIntent.java)
- [telegram-navigation.test.mjs](../../../rg-frontend-vaadin/src/test/frontend/ts/telegram-navigation.test.mjs)
