# UI foundations

## Purpose

Shared presentation supports narrow Telegram webviews and wider browsers without
changing business meaning. Capability-specific interactions are described in the
[UI index](README.md).

## Layout and actions

- Workspace screens use single-column, full-width layouts by default; wider arrangements
  use minimum-width breakpoints. Long names and translated action labels wrap.
- Shared rows and buttons retain a 44px minimum touch target.
- Text actions carrying `RGButtonTheme.BORDERED` use the `rg-bordered` theme token.
  Its CSS removes the border and supplies a raised shadow treatment with light/dark
  inset edges. Button variants communicate action intent through their theme colors;
  the shadow treatment is shared. Icon-only actions do not receive this token.
- Bordered buttons set `--vaadin-button-height: auto`. In a stretch-aligned flex row,
  sibling actions share the tallest wrapped label's height instead of clipping text.
- Disclosure headers, icon actions, status displays, and form controls expose accessible
  labels or semantic controls. Specific keyboard, focus, and live-status behavior belongs
  to the corresponding shared or capability contract.
- Contact-copy feedback respects reduced-motion preferences.

## Localization and time

Ukrainian (`uk-UA`) is the default locale; English is also supported. The shell language
selector updates the UI and session locale. Visible labels, validation, notifications,
and page titles resolve through `LocalizationService` and message bundles. Missing
translations use the service's fallback path rather than exposing raw exception text.

System-named workspaces display a localized label. User-entered names display verbatim.
Locale is a presentation choice, not an implicit timezone or phone-number region.

[Shared temporal interactions](shared-interactions.md#values-and-formatting) own date
formatting and input options. Event input and summary values use explicit UTC;
`LocalizationService.formatDateTime(Instant)` uses the system zone and localized
short date/time formatting.

## Related contracts

- [Navigation](navigation.md), [authentication](authentication.md), and [shared interactions](shared-interactions.md).
- [Workspace logic](../logic/workspace/README.md) and [participant disclosure](../logic/workspace/participants.md).

## Evidence

- [Application styles](../../../rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css)
- [LocalizationService.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/LocalizationService.java)
- [RGButtonTheme.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/button/RGButtonTheme.java)
- [WorkspaceResponsiveLocaleTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceResponsiveLocaleTest.java)
