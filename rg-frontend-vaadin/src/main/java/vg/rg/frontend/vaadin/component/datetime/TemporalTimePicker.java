package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.dom.Element;

import java.time.Duration;

/** Client-side layout adapter for grouped time choices in a {@code DateTimePicker}. */
final class TemporalTimePicker {
    private TemporalTimePicker() {
    }

    static void configure(Element dateTimePicker, Duration step) {
        var dropdownAvailable = step.compareTo(Duration.ofMinutes(15)) >= 0;
        dateTimePicker.executeJs("""
                const dateTimePicker = this;
                const configure = () => window.rgConfigureTemporalTimePicker(dateTimePicker, $0, $1);
                if (typeof window.rgConfigureTemporalTimePicker === 'function') {
                    configure();
                } else {
                    window.addEventListener('rg-temporal-time-picker-ready', configure, { once: true });
                }
                """, step.toSeconds(), dropdownAvailable);
    }
}
