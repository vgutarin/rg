# Shared interactions

Reusable contracts used by the [capability screens](README.md), with shared visual
rules in [foundations](foundations.md).

## Purpose and actors

View authors reuse disclosure, scrolling, location, and temporal controls so viewers
receive the same interaction behavior across capabilities.

## Interactions and outcomes

### Disclosure lists

`DisclosureList` owns accordion rows, chevrons, header accessibility, single-open
state, and labeled detail rows. Headers have `role="button"`, `tabindex="0"`, and
a click listener; expanded state is represented by the open CSS class. The component
sets neither `aria-expanded` nor `aria-controls` and installs no Enter/Space listener.
Decorative chevrons are hidden from assistive technology.

Each entry is keyed by domain identity. The open key survives a reset/rebuild, so an
edited or renamed row reopens even when its order changes. Deleted/filtered entries
do not reopen, and deliberately closed entries stay closed.

The component and its consumers share `disclosure-*` style constants. Panel-content
constants include `ROW_ACTIONS`, `DETAIL_DESCRIPTION`, and `DETAIL_META`; callers
apply them to their own content. `form-actions` and `stacked-form` provide shared
form presentation. Locations, participants, and events use this common component.

### Scroll cues

`ScrollCue` provides a scroll viewport with up/down chevrons that fade according to
content above/below the visible area. Visibility is updated in the browser from
scroll position and viewport/content size. `scrollByRows` shifts the viewport by a
row count. Event roster tabs use it to keep a moved row under the user's tapped control.

### Location selection

`LocationPicker` is a native filterable single-select field scoped to one workspace.
Blank input browses alphabetically ordered locations in 20-item pages; typing searches by
name using the domain service's configured result cap, with UI paging over that result.
Selection returns a
`LocationModel`, while arbitrary custom text cannot become a value. Restoring a
saved location identifier supports event editing. Search and matching rules belong
to [location logic](../logic/workspace/locations.md).

### Localized operation failures

Views that present a caught operation exception resolve its type through
`LocalizationService`. A Spring authorization denial shows a safe localized message
that the viewer lacks permission, without exposing the exception detail. Other
unrecognized exception types use the localized unknown-error fallback.

### Temporal controls

Reusable temporal helpers live in `vg.rg.frontend.vaadin.component.datetime`.

### Values and formatting

`DateTimes` formats `LocalDate` and `LocalDateTime` using an explicit locale and immutable,
builder-created `DateDisplayOptions`. Month/day ordering and time format follow the locale.
Options independently control the abbreviated weekday, year and seconds. Defaults show the year,
hide the weekday and hide seconds. The entry `step` defaults to `Duration.ofMinutes(15)`.
It must be a positive whole-second divisor of a day, at most one day; invalid steps are rejected.
Steps below 15 minutes disable Vaadin’s time dropdown and use typed entry. The step controls entry
precision independently of `showSeconds`, which controls the summary only. When a dropdown is
available, the inner mobile time input is read-only so tapping the field does not open the virtual
keyboard; the picker toggle still opens the dropdown. The opening press deliberately prevents input
focus; once that dropdown has opened, the next press on its time input enables typing and opens the
virtual keyboard. The dropdown opens scrolled to the field's current time. For steps that divide an hour (15, 20, 30 or
60 minutes), its choices are grouped into one clickable row per hour; a 15-minute row contains
`HH:00`, `HH:15`, `HH:30`, and `HH:45`. Fine-grained steps remain keyboard-editable.
For example, English with year hidden displays `Sep 9`, or
`Wed Sep 9` with the weekday enabled. Display options never truncate stored values.

`TemporalRange<T>` is immutable and constructed with its builder. Both endpoints are required,
and end must be greater than or equal to start. Null represents an absent value/range. Ranges
crossing a year boundary display both years even when the year preference is off.

Local datetimes have **no implicit timezone**. `DateTimes.toLocal(instant, zone)` and
`DateTimes.toInstant(local, zone)` require an explicit zone. The latter rejects DST gaps and
ambiguous overlaps rather than silently shifting a time or choosing an offset.
`LocalizationService.formatDateTime(Instant)` uses the system zone and Java `FormatStyle`.
`LocalizationService.formatEventDateTime(Instant,
DateDisplayOptions)` is a bridge that delegates to `DateTimes.format`, reading the instant as UTC and
using the current locale — so a caption formatted through the localization service and a temporal
picker built with the same options share one presentation.

### Components

`TemporalEditor.date`, `.dateTime`, `.dateRange`, and `.dateTimeRange` create typed Vaadin
`CustomField` components. They support `setValue`, `getValue`, value-change listeners, Binder,
`setReadOnly`, and `setDisplayOptions`. The label argument is a translation key.

```java
var appointment = TemporalEditor.dateTime(localization, "appointment.date");
appointment.setDisplayOptions(DateDisplayOptions.builder()
        .showShortDayName(true).showYear(false).step(Duration.ofMinutes(30)).build());
appointment.setValue(LocalDateTime.of(2026, 9, 9, 14, 30));
appointment.addValueChangeListener(event -> handleChange(event.getValue()));
```

The caller supplies translations for its own label keys. Ukrainian and English are supported by
the application's message bundles. Locale changes update display text, labels, calendar month/day
names, first weekday, actions and validation messages, including an open dialog, without resetting
its draft. Numeric entry formatting follows Vaadin's locale handling. With `showShortDayName`,
each date/datetime edit field also shows the localized weekday as helper text, updated when its
value or locale changes and removed when empty. It is not part of the typed date: Vaadin's Java
format API supports numeric day/month/year tokens only. Step and weekday option changes also
apply to an already-open dialog without resetting drafts. Each direct date-time picker owns the grouped-time
chooser module, so the module is loaded with the picker in optimized production bundles as well as in dialogs.

Create/Edit opens a modal dialog with required date/datetime fields (start/end for ranges).
Save validates completeness, input validity and ordering before publishing one value change.
Cancel and Escape discard the draft; clicking outside does not dismiss the dialog. Clear sets the
value to null. Read-only mode hides all editing actions. Changing options does not round existing values. Use a one-second step to enter seconds;
a coarser step hides seconds in the input, and choosing or typing a new time uses that precision. Forms and datetime sub-fields stack on narrow screens; example
cards gain a second column at 40rem.

## Routes and entry conditions

`/date-time-examples` is linked as **Dates & times** in the drawer only for callers
holding the app-wide `experiment:participant` permission. Its `beforeEnter` guard reroutes every other
caller to the no-access view, and `workspace:owner` alone grants no access. It demonstrates dates, datetimes, both ranges, an
empty value, and a read-only value, with live weekday/year/seconds/step controls and the shell's language
switcher. Examples are in-memory view state only and reset when the view is recreated.

## Functional requirements

- **FR-001**: Opening a disclosure entry MUST close the previously open entry; clicking it again MUST close it.
- **FR-002**: Rebuilding a disclosure list MUST restore its open entry by identity when that entry remains present.
- **FR-003**: A location picker MUST scope queries to its configured workspace, clear a selection when scope changes, and refuse arbitrary custom text as a value.
- **FR-004**: Temporal Save MUST validate completeness, input validity, and endpoint ordering before publishing a value change.
- **FR-005**: Cancel or Escape MUST discard a temporal dialog draft; Clear MUST set its value to null.
- **FR-006**: Locale and display-option changes MUST update temporal presentation without resetting the stored value or an open draft.
- **FR-007**: Converting a local datetime to an instant MUST require an explicit zone and refuse DST gaps and ambiguous overlaps.
- **FR-008**: The examples route MUST reject a caller without the app-wide experiment permission independently of workspace ownership.

## States and failures

Disclosure lists begin collapsed unless a remembered key is restored. Temporal
controls represent absence with null, and read-only mode hides editing actions.
Incomplete or reversed temporal ranges remain invalid drafts. Location pickers
return no options before scope is configured. Caught operation failures use the
localized mapping described above.

## Presentation and platform constraints

Temporal forms stack on narrow screens; example cards add a second column at
40rem. Browser scripts implement scroll cues and the grouped time chooser.
Caller-supplied informational glyphs require accessible labels; decorative chevrons
are hidden from assistive technology. Temporal localization follows the locale,
while zone conversion remains explicit.

## Invariants

- **INV-001**: For unique entry keys, at most one disclosure panel is open (checked by `DisclosureListTest`).
- **INV-002**: Temporal ranges have non-null endpoints and end is not before start (checked by `DateTimesTest`).
- **INV-003**: Entry step is a positive whole-second divisor of a day, no greater than one day (checked by `DateTimesTest`).
- **INV-004**: Changing display options does not round or truncate an existing temporal value (checked by `TemporalEditorTest`).

## Success criteria

- **SC-001**: Renaming/reordering an open disclosure entry and rebuilding the list preserves that entry's open state; removing it leaves no open panel (`DisclosureListTest`).
- **SC-002**: Canceling a temporal edit leaves its original value unchanged and saving a valid edit emits one value change (`TemporalEditorTest`).
- **SC-003**: Locale changes retranslate an open temporal dialog while preserving its draft (`TemporalEditorTest`).

## Related contracts

- [Foundations](foundations.md), [locations](locations.md), and [events](events.md).
- [Location logic](../logic/workspace/locations.md).

## Evidence

- [DisclosureList.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/disclosure/DisclosureList.java)
- [ScrollCue.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/scroll/ScrollCue.java)
- [LocationPicker.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/location/LocationPicker.java)
- [LocalizationService.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/service/LocalizationService.java)
- [TemporalEditor.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/TemporalEditor.java)
- [TemporalDateTimePicker.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/TemporalDateTimePicker.java)
- [DateTimes.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/DateTimes.java)
- [DisclosureListTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/disclosure/DisclosureListTest.java)
- [ScrollCueTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/scroll/ScrollCueTest.java)
- [LocationPickerTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/location/LocationPickerTest.java)
- [LocalizationServiceTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/service/LocalizationServiceTest.java)
- [DateTimesTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/DateTimesTest.java)
- [TemporalEditorTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/TemporalEditorTest.java)
- [TemporalDateTimePickerTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/TemporalDateTimePickerTest.java)
- [DateTimeExamplesViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/examples/datetime/DateTimeExamplesViewTest.java)
