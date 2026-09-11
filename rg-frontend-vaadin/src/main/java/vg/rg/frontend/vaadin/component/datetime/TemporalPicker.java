package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.stream.IntStream;

/** Internal adapter keeping the dialog identical for dates and datetimes. */
interface TemporalPicker<T> {
    Component component();
    T value();
    void value(T value);
    boolean invalid();
    void localize(LocalizationService localization, Locale locale, String label, DateDisplayOptions options);

    private static String weekday(java.time.temporal.TemporalAccessor value, Locale locale) {
        return java.time.format.DateTimeFormatter.ofPattern("EEE", locale).format(value);
    }

    static DatePicker.DatePickerI18n calendar(LocalizationService l, Locale locale) {
        return new DatePicker.DatePickerI18n()
                .setMonthNames(IntStream.rangeClosed(1, 12)
                        .mapToObj(i -> Month.of(i).getDisplayName(TextStyle.FULL_STANDALONE, locale)).toList())
                .setWeekdays(IntStream.range(0, 7)
                        .mapToObj(i -> DayOfWeek.of(i == 0 ? 7 : i).getDisplayName(TextStyle.FULL, locale)).toList())
                .setWeekdaysShort(IntStream.range(0, 7)
                        .mapToObj(i -> DayOfWeek.of(i == 0 ? 7 : i).getDisplayName(TextStyle.SHORT, locale)).toList())
                .setFirstDayOfWeek(WeekFields.of(locale).getFirstDayOfWeek().getValue() % 7)
                .setToday(l.getTranslation("dates.today", locale))
                .setCancel(l.getTranslation("dates.cancel", locale))
                .setRequiredErrorMessage(l.getTranslation("dates.required", locale))
                .setBadInputErrorMessage(l.getTranslation("dates.invalid", locale));
    }

    static TemporalPicker<LocalDate> date() {
        var field = new DatePicker();
        field.setWidthFull();
        field.setRequiredIndicatorVisible(true);
        return new TemporalPicker<>() {
            private Locale currentLocale;
            private DateDisplayOptions options;
            { field.addValueChangeListener(event -> updateWeekday()); }
            private void updateWeekday() {
                field.setHelperText(options != null && options.isShowShortDayName()
                        && field.getValue() != null ? weekday(field.getValue(), currentLocale) : "");
            }
            public Component component() { return field; }
            public LocalDate value() { return field.getValue(); }
            public void value(LocalDate value) { field.setValue(value); }
            public boolean invalid() { return field.isInvalid(); }
            public void localize(LocalizationService l, Locale locale, String label, DateDisplayOptions options) {
                this.currentLocale = locale;
                this.options = options;
                updateWeekday();
                field.setLabel(label);
                field.setLocale(locale);
                field.setI18n(calendar(l, locale));
            }
        };
    }

    static TemporalPicker<LocalDateTime> dateTime() {
        var field = new TemporalDateTimePicker();
        field.setWidthFull();
        field.addClassName("temporal-picker");
        field.setRequiredIndicatorVisible(true);
        return new TemporalPicker<>() {
            private Locale currentLocale;
            private DateDisplayOptions options;
            private String label;
            { field.addValueChangeListener(event -> updateWeekday()); }
            private void updateWeekday() {
                if (label == null) return;
                field.setLabel(options != null && options.isShowShortDayName() && field.getValue() != null
                        ? label + " (" + weekday(field.getValue(), currentLocale) + ")" : label);
            }
            public Component component() { return field; }
            public LocalDateTime value() { return field.getValue(); }
            public void value(LocalDateTime value) { field.setValue(value); }
            public boolean invalid() { return field.isInvalid(); }
            public void localize(LocalizationService l, Locale locale, String label, DateDisplayOptions options) {
                this.currentLocale = locale;
                this.options = options;
                this.label = label;
                updateWeekday();
                field.setLocale(locale);
                field.setStep(options.getStep());
                TemporalTimePicker.configure(field.getElement(), options.getStep());
                field.setDatePickerI18n(calendar(l, locale));
                field.setDateAriaLabel(l.getTranslation("dates.date", locale));
                field.setTimeAriaLabel(l.getTranslation("dates.time", locale));
                field.setI18n(new DateTimePicker.DateTimePickerI18n()
                        .setRequiredErrorMessage(l.getTranslation("dates.required", locale))
                        .setBadInputErrorMessage(l.getTranslation("dates.invalid", locale))
                        .setIncompleteInputErrorMessage(l.getTranslation("dates.incomplete", locale)));
            }
        };
    }
}
