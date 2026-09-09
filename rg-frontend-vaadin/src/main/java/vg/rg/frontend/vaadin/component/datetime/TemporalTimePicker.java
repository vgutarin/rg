package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.dom.Element;

import java.time.Duration;

/** Client-side layout adapter for grouped time choices in a {@code DateTimePicker}. */
final class TemporalTimePicker {
    private TemporalTimePicker() {
    }

    static void configure(Element dateTimePicker, Duration step) {
        var hasDropdown = step.compareTo(Duration.ofMinutes(15)) >= 0;
        dateTimePicker.executeJs("window.rgConfigureTemporalTimePicker(this, $0, $1)",
                step.toSeconds(), hasDropdown);
    }
}
