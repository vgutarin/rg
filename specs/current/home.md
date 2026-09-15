# Home and navigation

Current-state specification of the application's home page and first-level navigation.

## Top-level destinations

The drawer presents the ordered navigation provider as Android-style tiles in three columns: Home, Dates &
times, Locations, Participants, and Events. Each full tile is an icon-and-label route link and retains the
existing compact tile dimensions. Home is always shown. Dates & times requires `experiment:participant` and
does not resolve or provision a workspace. Locations, Participants, and Events are included only when the
caller holds `workspace:owner`, has an active workspace, and passes the corresponding scoped list check.
The provider resolves that workspace once per render before evaluating those three entries.

The workspace-management route, login, and access-status routes remain outside user navigation.

## Telegram native back navigation

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

## Home

The home page shows only a localized welcome message. Navigation is available from the drawer tile panel.
