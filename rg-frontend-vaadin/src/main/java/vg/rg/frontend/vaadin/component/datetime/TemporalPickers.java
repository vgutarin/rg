package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.Objects;

/** Creates direct date-time inputs with the same localization and entry options as temporal editors. */
public final class TemporalPickers {
    private TemporalPickers() {
    }

    public static DateTimePicker newDateTimePicker(LocalizationService localization, String labelKey,
                                                    DateDisplayOptions options) {
        Objects.requireNonNull(localization, "localization");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(options, "options");

        var locale = localization.getCurrentLocale();
        var picker = TemporalPicker.dateTime();
        picker.localize(localization, locale, localization.getTranslation(labelKey, locale), options);
        return (DateTimePicker) picker.component();
    }
}
