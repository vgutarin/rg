# Shared interactions

Reusable contracts used by the [capability screens](README.md), with shared visual
rules in [foundations](foundations.md).

## Disclosure lists

`DisclosureList` owns accordion rows, chevrons, header accessibility, single-open
state, and labeled detail rows. Headers expose their expanded state and controls;
decorative chevrons are hidden from assistive technology.

Each entry is keyed by domain identity. The open key survives a reset/rebuild, so an
edited or renamed row reopens even when its order changes. Deleted/filtered entries
do not reopen, and deliberately closed entries stay closed.

The component and its consumers share `disclosure-*` style constants. Panel-content
constants include `ROW_ACTIONS`, `DETAIL_DESCRIPTION`, and `DETAIL_META`; callers
apply them to their own content. `form-actions` and `stacked-form` provide shared
form presentation. Locations, participants, and events use this common component.

## Scroll cues

`ScrollCue` provides a scroll viewport with up/down chevrons that fade according to
content above/below the visible area. Visibility is updated in the browser from
scroll position and viewport/content size. `scrollByRows` shifts the viewport by a
row count. Event roster tabs use it to keep a moved row under the user's tapped control.

## Location selection

`LocationPicker` is a native filterable single-select field scoped to one workspace.
Blank input loads the first 20 alphabetically ordered locations; typing searches by
name. The domain service caps and orders the result. Selection returns a
`LocationModel`, while arbitrary custom text cannot become a value. Restoring a
saved location identifier supports event editing. Search and matching rules belong
to [location logic](../logic/workspace/locations.md).

## Temporal controls

Reusable temporal helpers live in `vg.rg.frontend.vaadin.component.datetime`.

## Values and formatting

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

## Components

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

## Examples

`/date-time-examples` is linked as **Dates & times** in the drawer only for callers
holding the app-wide `experiment:participant` permission. Its `beforeEnter` guard reroutes every other
caller to the no-access view, and `workspace:owner` alone grants no access. It demonstrates dates, datetimes, both ranges, an
empty value, and a read-only value, with live weekday/year/seconds/step controls and the shell's language
switcher. Examples are in-memory view state only and reset when the view is recreated.

## Evidence

- [DisclosureList.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/disclosure/DisclosureList.java)
- [ScrollCue.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/scroll/ScrollCue.java)
- [LocationPicker.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/location/LocationPicker.java)
- [TemporalEditor.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/TemporalEditor.java)
- [TemporalDateTimePicker.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/TemporalDateTimePicker.java)
- [DateTimes.java](../../../rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/component/datetime/DateTimes.java)
- [DisclosureListTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/disclosure/DisclosureListTest.java)
- [ScrollCueTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/scroll/ScrollCueTest.java)
- [LocationPickerTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/location/LocationPickerTest.java)
- [DateTimesTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/DateTimesTest.java)
- [TemporalEditorTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/TemporalEditorTest.java)
- [TemporalDateTimePickerTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/component/datetime/TemporalDateTimePickerTest.java)
- [DateTimeExamplesViewTest.java](../../../rg-frontend-vaadin/src/test/java/vg/rg/frontend/vaadin/view/examples/datetime/DateTimeExamplesViewTest.java)
