# UI foundations

## Purpose and actors

Shared presentation supports narrow Telegram webviews and wider browsers without
changing business meaning. Capability-specific interactions are described in the
[UI index](README.md).

## Presentation and platform constraints

### Layout and actions

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

### Localization and time

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

## Functional requirements

- **FR-001**: Workspace screens MUST default to single-column narrow layouts and introduce wider arrangements through minimum-width breakpoints.
- **FR-002**: Workspace names and translated action labels MUST wrap rather than be truncated by a fixed width.
- **FR-003**: Changing the shell's language MUST update both UI and session locale.
- **FR-004**: Shared localization MUST provide Ukrainian by default and English as the other supported locale.
- **FR-005**: Event input and summaries MUST use explicit UTC independently of the selected locale.

## States and failures

A missing English translation falls back to Ukrainian. A key missing from both
bundles resolves to a safe localized fallback. Unrecognized operation exceptions
use the localized unknown-error message, as defined by
[shared failure presentation](shared-interactions.md#localized-operation-failures).

## Invariants

- **INV-001**: Stored user-entered workspace names display verbatim rather than using the system-name translation (checked by `WorkspaceLayoutTest`).
- **INV-002**: Workspace wide-layout media queries use `min-width` (checked by `WorkspaceResponsiveLocaleTest`).
- **INV-003**: Missing translations do not expose their raw key through the localization fallback path (checked by `LocalizationServiceTest`).

## Success criteria

- **SC-001**: Workspace message keys resolve in both supported bundles (`WorkspaceResponsiveLocaleTest`).
- **SC-002**: A maximum-length workspace name renders in full without a fixed-width clipping rule (`WorkspaceResponsiveLocaleTest`).
- **SC-003**: Selecting English updates the server session locale (`MainViewTest`, `LocaleSessionIntegrationTest`).

## Related contracts

- [Navigation](navigation.md), [authentication](authentication.md), and [shared interactions](shared-interactions.md).
- [Workspace logic](../logic/workspace/README.md) and [participant disclosure](../logic/workspace/participants.md).

## Evidence

- [Application styles](../../../rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css)
- [LocalizationService.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/LocalizationService.java)
- [RGButtonTheme.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/button/RGButtonTheme.java)
- [WorkspaceResponsiveLocaleTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceResponsiveLocaleTest.java)
- [LocalizationServiceTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocalizationServiceTest.java)
- [WorkspaceLayoutTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/workspace/WorkspaceLayoutTest.java)
- [MainViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/MainViewTest.java)
- [LocaleSessionIntegrationTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocaleSessionIntegrationTest.java)
