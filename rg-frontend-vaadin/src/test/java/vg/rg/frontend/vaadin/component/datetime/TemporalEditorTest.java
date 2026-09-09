package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import org.junit.jupiter.api.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.time.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class TemporalEditorTest {
    private UI ui;
    private LocalizationService localization;

    @BeforeEach void setup() {
        ui = new UI();
        UI.setCurrent(ui);
        ui.setLocale(LocalizationService.DEFAULT_LOCALE);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        localization = new LocalizationService(messages);
    }
    @AfterEach void cleanup() { UI.setCurrent(null); }

    @Test void editingIsTransactionalAndFiresOneValueChangeOnlyOnSave() {
        var editor = TemporalEditor.dateTime(localization, "dates.datetime");
        var original = LocalDateTime.of(2026, 9, 9, 14, 30, 15);
        editor.setValue(original);
        ui.add(editor);
        var events = new AtomicInteger();
        editor.addValueChangeListener(event -> events.incrementAndGet());
        var dialog = open(editor);
        var field = find(dialog, DateTimePicker.class).findFirst().orElseThrow();
        field.setValue(original.plusDays(1));
        assertThat(editor.getValue()).isEqualTo(original);
        dialog.close();
        assertThat(editor.getValue()).isEqualTo(original);
        assertThat(events).hasValue(0);
        dialog = open(editor);
        field = find(dialog, DateTimePicker.class).findFirst().orElseThrow();
        assertThat(field.getValue()).isEqualTo(original);
        field.setValue(original.plusDays(2));
        dialog.save();
        assertThat(editor.getValue()).isEqualTo(original.plusDays(2));
        assertThat(events).hasValue(1);
        dialog.save();
        assertThat(events).hasValue(1);
    }

    @Test void rangesRequireBothEndpointsAndRejectReversedValues() {
        var editor = TemporalEditor.dateRange(localization, "dates.date-range");
        ui.add(editor);
        var dialog = open(editor);
        var fields = find(dialog, DatePicker.class).toList();
        var start = LocalDate.of(2026, 9, 9);
        fields.getFirst().setValue(start);
        dialog.save();
        assertThat(dialog.isOpened()).isTrue();
        assertThat(editor.getValue()).isNull();
        fields.getLast().setValue(start.minusDays(1));
        dialog.save();
        assertThat(editor.getValue()).isNull();
        fields.getLast().setValue(start);
        dialog.save();
        assertThat(editor.getValue()).isEqualTo(TemporalRange.<LocalDate>builder().start(start).end(start).build());
    }

    @Test void localeChangePreservesValueAndOpenDraftAndTranslatesCalendar() {
        var editor = TemporalEditor.date(localization, "dates.date");
        editor.setValue(LocalDate.of(2026, 9, 9));
        ui.add(editor);
        var dialog = open(editor);
        var field = find(dialog, DatePicker.class).findFirst().orElseThrow();
        var draft = LocalDate.of(2026, 9, 10);
        field.setValue(draft);
        var event = new LocaleChangeEvent(ui, Locale.ENGLISH);
        editor.localeChange(event);
        dialog.localeChange(event);
        assertThat(editor.getLabel()).isEqualTo("Date");
        assertThat(field.getValue()).isEqualTo(draft);
        assertThat(field.getI18n().getToday()).isEqualTo("Today");
        assertThat(field.getI18n().getMonthNames()).contains("September");
        assertThat(editor.getValue()).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test void readonlyHidesActionsAndDisplayOptionsDoNotMutateValue() {
        var editor = TemporalEditor.date(localization, "dates.date");
        var date = LocalDate.of(2026, 9, 9);
        editor.setValue(date);
        editor.localeChange(new LocaleChangeEvent(ui, Locale.ENGLISH));
        editor.setDisplayOptions(DateDisplayOptions.builder().showYear(false).showShortDayName(true).build());
        assertThat(find(editor, Div.class).filter(div -> div.hasClassName("temporal-value"))
                .findFirst().orElseThrow().getText()).isEqualTo("Wed Sep 9");
        editor.setReadOnly(true);
        assertThat(find(editor, Button.class).toList()).allMatch(button -> !button.isVisible());
        assertThat(editor.getValue()).isEqualTo(date);
    }

    @Test void datetimeRangeRejectsInvalidInputAndSupportsOvernightValues() {
        var editor = TemporalEditor.dateTimeRange(localization, "dates.datetime-range");
        ui.add(editor);
        var dialog = open(editor);
        var fields = find(dialog, DateTimePicker.class).toList();
        var start = LocalDateTime.of(2026, 9, 9, 23, 30);
        fields.getFirst().setValue(start);
        fields.getLast().setValue(start.plusHours(2));
        fields.getLast().setInvalid(true);
        dialog.save();
        assertThat(editor.getValue()).isNull();
        fields.getLast().setInvalid(false);
        dialog.save();
        assertThat(editor.getValue().getEnd()).isEqualTo(start.plusHours(2));
        find(editor, Button.class).toList().getLast().click();
        assertThat(editor.getValue()).isNull();
    }

    @Test void crossYearRangesAlwaysShowBothYears() {
        var editor = TemporalEditor.dateRange(localization, "dates.date-range");
        editor.setValue(TemporalRange.<LocalDate>builder()
                .start(LocalDate.of(2026, 12, 31)).end(LocalDate.of(2027, 1, 1)).build());
        editor.setDisplayOptions(DateDisplayOptions.builder().showYear(false).build());
        assertThat(find(editor, Div.class).filter(div -> div.hasClassName("temporal-value"))
                .findFirst().orElseThrow().getText()).contains("2026", "2027");
    }

    @Test void examplesExposeAllFourTypesAndFormattingControls() {
        var view = new vg.rg.frontend.vaadin.view.examples.datetime.DateTimeExamplesView(localization);
        ui.add(view);
        var editors = find(view, TemporalEditor.class).toList();
        assertThat(editors).hasSize(6);
        assertThat(editors).filteredOn(TemporalEditor::isReadOnly).hasSize(1);
        var settings = find(view, com.vaadin.flow.component.checkbox.Checkbox.class).toList();
        assertThat(settings).hasSize(3);
        settings.get(1).setValue(true);
        assertThat(find(editors.getFirst(), Div.class).filter(div -> div.hasClassName("temporal-value"))
                .findFirst().orElseThrow().getText()).contains("2026");
    }

    @Test void stepAndWeekdayOptionsUpdateBothOpenRangeFieldsWithoutChangingDrafts() {
        var editor = TemporalEditor.dateTimeRange(localization, "dates.datetime-range");
        ui.add(editor);
        var dialog = open(editor);
        dialog.localeChange(new LocaleChangeEvent(ui, Locale.ENGLISH));
        var fields = find(dialog, DateTimePicker.class).toList();
        assertThat(fields).allSatisfy(field -> assertThat(field.getStep()).isEqualTo(Duration.ofMinutes(15)));
        var draft = LocalDateTime.of(2026, 9, 9, 14, 30, 15);
        fields.getFirst().setValue(draft);
        fields.getLast().setValue(draft.plusDays(1));
        editor.setDisplayOptions(DateDisplayOptions.builder().showShortDayName(true)
                .step(Duration.ofSeconds(1)).build());
        assertThat(fields).allSatisfy(field -> assertThat(field.getStep()).isEqualTo(Duration.ofSeconds(1)));
        assertThat(fields.getFirst().getHelperText()).isEqualTo("Wed");
        assertThat(fields.getLast().getHelperText()).isEqualTo("Thu");
        assertThat(fields.getFirst().getValue()).isEqualTo(draft);
        fields.getFirst().setValue(draft.plusDays(2));
        assertThat(fields.getFirst().getHelperText()).isEqualTo("Fri");
        dialog.localeChange(new LocaleChangeEvent(ui, LocalizationService.DEFAULT_LOCALE));
        assertThat(fields.getFirst().getHelperText()).isEqualTo("пт");
        editor.setDisplayOptions(DateDisplayOptions.builder().build());
        assertThat(fields.getFirst().getHelperText()).isEmpty();
        assertThat(editor.getValue()).isNull();
    }

    @Test void dateWeekdayTracksValueAndClearing() {
        var editor = TemporalEditor.date(localization, "dates.date");
        editor.setDisplayOptions(DateDisplayOptions.builder().showShortDayName(true).build());
        ui.add(editor);
        var dialog = open(editor);
        dialog.localeChange(new LocaleChangeEvent(ui, Locale.ENGLISH));
        var field = find(dialog, DatePicker.class).findFirst().orElseThrow();
        field.setValue(LocalDate.of(2026, 9, 9));
        assertThat(field.getHelperText()).isEqualTo("Wed");
        field.clear();
        assertThat(field.getHelperText()).isEmpty();
    }

    private TemporalEditDialog<?> open(TemporalEditor<?> editor) {
        find(editor, Button.class).findFirst().orElseThrow().click();
        return find(editor, TemporalEditDialog.class).findFirst().orElseThrow();
    }

    private <T> Stream<T> find(Component parent, Class<T> type) {
        return Stream.concat(type.isInstance(parent) ? Stream.of(type.cast(parent)) : Stream.empty(),
                parent.getChildren().flatMap(child -> find(child, type)));
    }
}
