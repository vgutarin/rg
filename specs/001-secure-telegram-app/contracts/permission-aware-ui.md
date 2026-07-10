# Permission-Aware UI Contract

## Visual direction

The application is calm, confident, and lightweight. It uses Aura with a sky accent, neutral paired
light/dark backgrounds, high contrast, spacious mobile density, restrained rounded surfaces, and a
small number of purposeful elevations. No avatar, profile label, or Telegram-derived personalization
is permitted.

## Responsive shell

- Base viewport: 320px wide Telegram webview; no horizontal page scrolling.
- Primary shell: Vaadin `AppLayout` with drawer section, `DrawerToggle`, `Scroller`, and `SideNav`.
- On narrow screens the drawer is an overlay. On wider screens Vaadin may pin the drawer alongside
  content. Wider composition is introduced only with `min-width` media queries.
- Header contains drawer toggle, current localized title, compact locale control, and logout. It
  never displays the authenticated `sub`, identity display name, or Telegram identity.
- Locale control contains exactly Ukrainian and English with localized option labels. Ukrainian is
  selected in every new session; a manual choice remains active through navigation and reload only
  within that session.
- Side navigation uses icon plus text and Aura's filled current-item styling. Items are instantiated
  only for recognized permissions.

## Permission mapping

| Permission | Visible UI | Direct access behavior |
|------------|------------|------------------------|
| `home:view` | Home item and landing content | Require non-null `sub` and the current session permission through `AuthorityChecker`; deny/reroute on failure |
| `reports:view` | Reports item | Require non-null `sub` and the current session permission through `AuthorityChecker`; deny/reroute on failure |
| `request:submit` | Primary protected action | Check current permission, require non-null `sub`, and require an idempotency key |
| Missing/unknown | No item/action | Grants nothing |

Navigation filtering is never accepted as authorization evidence.

The current session principal remains authoritative until reauthentication or authenticated-session
replacement. Identity-service permission changes do not affect the existing principal, and no route
or action performs per-operation remote reauthorization. A null `sub` or false consent flag is valid
session state, but null `sub` suppresses every protected capability regardless of permissions. False
consent does not suppress recognized permissions when `sub` is non-null.

## Authentication and state behavior

| State | Content | Primary action | Accessibility behavior |
|-------|---------|----------------|------------------------|
| Loading | Progress indicator, “Checking secure access” | None | `aria-live="polite"`; no stale protected content |
| Permitted | Allowed navigation/content only | First allowed task | Move focus only when user initiated navigation |
| No access | Empty-state card, concise guidance | Logout/close | Heading explains that identity is valid but no app access exists; used for null `sub` or no recognized permissions |
| Denied | Shield/error state, no internal reason | Retry only if request may safely be repeated | Focus heading; error not expressed by color alone |
| Temporarily unavailable | Offline/service state | Retry | State persists until explicit retry; bounded retry feedback |
| Incompatible | Update/support guidance | Retry after deployment only | No raw version or response body displayed |
| Retrying | Busy retry button and progress text | Disabled | Prevent repeated activation and announce progress |

## Component composition

- `TelegramAuthView`: centered status card with shield mark, heading, short description, progress bar,
  and a single retry button shown only for recoverable outcomes.
- `LandingView`: compact welcome heading using non-personal copy, a “Your access” capability summary,
  and one prominent permitted action. Cards stack on mobile and form a modest grid at wider widths.
- `ReportsView`: representative protected content with a concise title and empty/data state; no
  identity data.
- `NoAccessView` and `AccessDeniedErrorView`: reusable semantic status composition.
- Buttons use built-in primary/tertiary variants where supported; secondary buttons use the Aura
  neutral-button token pattern. Every touch action meets a 44px-equivalent target.

## Aura token direction

- Accent: sky `#0084d1` for light and `#38bdf8` for dark.
- Background: neutral light/dark pair.
- Contrast: high (`--aura-contrast-level: 2`).
- Density: spacious (`--aura-base-size: 20`).
- Base font size: 15.
- Radius: rounded but restrained (`--aura-base-radius: 4`).
- App layout inset: `0px` for a Telegram-webview-friendly edge-to-edge shell.
- Use system color preference already enabled by `@ColorScheme(LIGHT_DARK)`.
- Use only valid Aura or shared `--vaadin-*` properties; no Lumo utilities or Lumo-only variants.

## Interaction rules

1. Do not render protected content until the current session principal has non-null `sub` and the
   required permission passes `AuthorityChecker` validation.
2. Disable action immediately after click and attach one generated idempotency key to all repeats of
   the same logical attempt.
3. Do not automatically retry protected mutations after an ambiguous timeout.
4. Close the overlay drawer after navigation on narrow screens.
5. Preserve visible focus and logical DOM/tab order.
6. Localize every visible string; tolerate long labels without truncating essential actions.
7. Never render raw errors, Telegram fields, authenticated `sub` or identity display-name values, request/operation IDs, or service details.
8. Resolve page titles, navigation, controls, statuses, validation, notifications, and assistive text
   from semantic translation keys; use placeholders rather than concatenating translated fragments.
9. Map secure-service failures from validated domain outcomes to local message keys. Never display
   raw diagnostic details or unknown failure codes directly.
10. For every permission set with an allowed primary action, make that action visible, clearly
    labeled, keyboard-focusable, and startable from the landing experience within at most two user
    activations. Click, tap, Enter, or Space counts; page load, focus movement, and scrolling do not.

## Locale lifecycle

| Event | Required behavior |
|-------|-------------------|
| New application session | Select Ukrainian regardless of browser, JVM, or Telegram locale |
| Select English | Refresh all current visible and assistive text before the next action |
| Select Ukrainian | Refresh all current visible and assistive text before the next action |
| Navigate or reload in same session | Retain the manual locale choice |
| Unsupported or null locale | Normalize to Ukrainian |
| Missing English key | Render the Ukrainian translation |
| Missing key in every bundle | Render a localized Ukrainian safety message, never the key or blank |
| New session after prior English session | Reset to Ukrainian |

Every locale refresh preserves the current route path/query, Spring authentication, opaque
principal, permission set, and protected-content authorization. It must not call
the secure facade solely because the locale changed. Locale is not persisted to browser storage,
cookies, a database, or the secure-service contract.

## Acceptance viewport matrix

| Viewport | Required result |
|----------|-----------------|
| 320 x 640 | Single-column content, overlay drawer, all controls reachable, no horizontal scroll |
| 390 x 844 | Comfortable Telegram mobile layout and touch targets |
| 768 x 1024 | Drawer/content transition remains coherent; cards may become two columns |
| 1280 x 800 | Pinned navigation and bounded readable content width; no excessive whitespace |

For every viewport, repeat loading, permitted, no-access, denied, unavailable, incompatible, and
retry flows with keyboard-only navigation, light/dark schemes, Ukrainian and English, long labels,
and localized accessibility text.
