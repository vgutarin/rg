package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A bindable display with transactional dialog editing. Factories preserve value types:
 * LocalDate, LocalDateTime, or TemporalRange of either. Null means no value.
 */
public class TemporalEditor<V> extends CustomField<V> implements LocaleChangeObserver {
    private final LocalizationService localization;
    private final String labelKey;
    private final Function<V, String> formatter;
    private final Runnable openEditor;
    private final Div display = new Div();
    private final Button edit = new Button();
    private final Button clear = new Button();
    private V value;
    private Locale locale;
    private DateDisplayOptions displayOptions = DateDisplayOptions.builder().build();

    private <T extends Comparable<? super T>> TemporalEditor(
            LocalizationService localization, String labelKey, Supplier<TemporalPicker<T>> pickers,
            boolean range, Function<V, TemporalRange<T>> toRange, Function<TemporalRange<T>, V> fromRange,
            ValueFormatter<V> formatter) {
        this.localization = localization;
        this.labelKey = labelKey;
        this.locale = localization.getCurrentLocale();
        this.formatter = v -> formatter.format(v, locale, displayOptions);
        openEditor = () -> {
            if (isReadOnly() || !isEnabled()) return;
            var dialog = new TemporalEditDialog<>(localization, labelKey, pickers, range,
                    value == null ? null : toRange.apply(value), locale, displayOptions, saved -> {
                        if (isReadOnly() || !isEnabled()) return;
                        value = fromRange.apply(saved);
                        setModelValue(value, true);
                        render();
                    });
            // Ownership lets Vaadin propagate live locale changes and clean up with this view.
            add(dialog);
            dialog.addOpenedChangeListener(event -> {
                if (!event.isOpened()) getElement().removeChild(dialog.getElement());
            });
            dialog.open();
        };
        addClassName("temporal-editor");
        display.addClassName("temporal-value");
        display.getElement().setAttribute("aria-live", "polite");
        edit.addClickListener(event -> openEditor.run());
        clear.addClickListener(event -> {
            if (isReadOnly()) return;
            value = null;
            setModelValue(null, true);
            render();
        });
        var actions = new Div(edit, clear);
        actions.addClassName("temporal-actions");
        add(display, actions);
        render();
    }

    public static TemporalEditor<LocalDate> date(LocalizationService l, String labelKey) {
        return new TemporalEditor<>(l, labelKey, TemporalPicker::date, false,
                v -> TemporalRange.<LocalDate>builder().start(v).end(v).build(), TemporalRange::getStart,
                DateTimes::format);
    }

    public static TemporalEditor<LocalDateTime> dateTime(LocalizationService l, String labelKey) {
        return new TemporalEditor<>(l, labelKey, TemporalPicker::dateTime, false,
                v -> TemporalRange.<LocalDateTime>builder().start(v).end(v).build(), TemporalRange::getStart,
                DateTimes::format);
    }

    public static TemporalEditor<TemporalRange<LocalDate>> dateRange(LocalizationService l, String labelKey) {
        return new TemporalEditor<>(l, labelKey, TemporalPicker::date, true, Function.identity(), Function.identity(),
                (v, locale, options) -> {
                    var rangeOptions = rangeOptions(v.getStart().getYear(), v.getEnd().getYear(), options);
                    return DateTimes.format(v.getStart(), locale, rangeOptions) + " – "
                            + DateTimes.format(v.getEnd(), locale, rangeOptions);
                });
    }

    public static TemporalEditor<TemporalRange<LocalDateTime>> dateTimeRange(LocalizationService l, String labelKey) {
        return new TemporalEditor<>(l, labelKey, TemporalPicker::dateTime, true, Function.identity(), Function.identity(),
                (v, locale, options) -> {
                    var rangeOptions = rangeOptions(v.getStart().getYear(), v.getEnd().getYear(), options);
                    return DateTimes.format(v.getStart(), locale, rangeOptions) + " – "
                            + DateTimes.format(v.getEnd(), locale, rangeOptions);
                });
    }

    private static DateDisplayOptions rangeOptions(int startYear, int endYear, DateDisplayOptions options) {
        return startYear == endYear ? options : options.toBuilder().showYear(true).build();
    }

    public void setDisplayOptions(DateDisplayOptions options) {
        displayOptions = Objects.requireNonNull(options, "options");
        getChildren().filter(TemporalEditDialog.class::isInstance)
                .map(TemporalEditDialog.class::cast).forEach(dialog -> dialog.setDisplayOptions(options));
        render();
    }

    @Override public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        render();
    }

    @Override protected V generateModelValue() { return value; }
    @Override protected void setPresentationValue(V value) { this.value = value; render(); }

    @Override public void localeChange(LocaleChangeEvent event) {
        locale = event.getLocale();
        render();
    }

    private void render() {
        setLabel(text(labelKey));
        display.setText(value == null ? text("dates.empty") : formatter.apply(value));
        edit.setText(text(value == null ? "dates.create" : "dates.edit"));
        edit.setAriaLabel(edit.getText() + " · " + text(labelKey));
        clear.setText(text("dates.clear"));
        clear.setAriaLabel(clear.getText() + " · " + text(labelKey));
        edit.setVisible(!isReadOnly());
        clear.setVisible(!isReadOnly() && value != null);
    }

    private String text(String key) { return localization.getTranslation(key, locale); }

    @FunctionalInterface
    private interface ValueFormatter<V> {
        String format(V value, Locale locale, DateDisplayOptions options);
    }
}
