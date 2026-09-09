package vg.rg.frontend.vaadin.view.examples.datetime;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.view.MainView;
import vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions;
import vg.rg.frontend.vaadin.component.datetime.TemporalEditor;
import vg.rg.frontend.vaadin.component.datetime.TemporalRange;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.time.Duration;
import com.vaadin.flow.component.select.Select;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Route(value = "date-time-examples", layout = MainView.class)
@PageTitle("dates.examples.title")
@PermitAll
public class DateTimeExamplesView extends VerticalLayout implements LocaleChangeObserver {
    private final LocalizationService localization;
    private final H1 title = new H1();
    private final Paragraph description = new Paragraph();
    private final Checkbox weekday = new Checkbox();
    private final Checkbox year = new Checkbox();
    private final Checkbox seconds = new Checkbox();
    private final Select<Duration> step = new Select<>();
    private final List<TemporalEditor<?>> examples;

    public DateTimeExamplesView(LocalizationService localization) {
        this.localization = localization;
        addClassNames("secure-view", "date-time-examples");
        var date = TemporalEditor.date(localization, "dates.date");
        date.setValue(LocalDate.of(2026, 9, 9));
        var datetime = TemporalEditor.dateTime(localization, "dates.datetime");
        datetime.setValue(LocalDateTime.of(2026, 9, 9, 14, 30, 15));
        var dateRange = TemporalEditor.dateRange(localization, "dates.date-range");
        dateRange.setValue(TemporalRange.<LocalDate>builder()
                .start(LocalDate.of(2026, 9, 9)).end(LocalDate.of(2026, 9, 12)).build());
        var datetimeRange = TemporalEditor.dateTimeRange(localization, "dates.datetime-range");
        datetimeRange.setValue(TemporalRange.<LocalDateTime>builder()
                .start(LocalDateTime.of(2026, 9, 9, 23, 30)).end(LocalDateTime.of(2026, 9, 10, 1, 0)).build());
        var empty = TemporalEditor.dateTime(localization, "dates.examples.empty");
        var readOnly = TemporalEditor.dateTime(localization, "dates.examples.read-only");
        readOnly.setValue(LocalDateTime.of(2026, 9, 9, 9, 0));
        readOnly.setReadOnly(true);
        examples = List.of(date, datetime, dateRange, datetimeRange, empty, readOnly);
        step.setItems(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1),
                Duration.ofMinutes(1), Duration.ofSeconds(1));
        step.setValue(Duration.ofMinutes(15));
        step.addValueChangeListener(event -> updateOptions());
        var settings = new FormLayout(weekday, year, seconds, step);
        settings.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("40rem", 3));
        settings.addClassNames("semantic-card", "aura-surface");
        weekday.setValue(true);
        weekday.addValueChangeListener(event -> updateOptions());
        year.addValueChangeListener(event -> updateOptions());
        seconds.addValueChangeListener(event -> updateOptions());
        var cards = new Div();
        cards.addClassName("temporal-examples-grid");
        examples.forEach(editor -> {
            var card = new Div(editor);
            card.addClassNames("semantic-card", "aura-surface");
            cards.add(card);
        });
        add(title, description, settings, cards);
        updateOptions();
        renderTranslations();
    }

    @Override public void localeChange(LocaleChangeEvent event) { renderTranslations(); }

    private void updateOptions() {
        var options = DateDisplayOptions.builder().showShortDayName(weekday.getValue())
                .showYear(year.getValue()).showSeconds(seconds.getValue()).step(step.getValue()).build();
        examples.forEach(editor -> editor.setDisplayOptions(options));
    }

    private void renderTranslations() {
        step.setLabel(localization.i18n("dates.options.step"));
        step.setHelperText(localization.i18n("dates.options.step-help"));
        step.setItemLabelGenerator(value -> value.getSeconds() < 60
                ? value.getSeconds() + " " + localization.i18n("dates.unit.seconds")
                : value.toMinutes() + " " + localization.i18n("dates.unit.minutes"));
        title.setText(localization.i18n("dates.examples.title"));
        description.setText(localization.i18n("dates.examples.description"));
        weekday.setLabel(localization.i18n("dates.options.weekday"));
        year.setLabel(localization.i18n("dates.options.year"));
        seconds.setLabel(localization.i18n("dates.options.seconds"));
    }
}
