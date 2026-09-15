package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dependency.JsModule;

/** Date-time picker that owns the client-side grouped-time chooser dependency. */
@JsModule("./ts/datetime/temporal-time-picker.ts")
final class TemporalDateTimePicker extends DateTimePicker {
}
