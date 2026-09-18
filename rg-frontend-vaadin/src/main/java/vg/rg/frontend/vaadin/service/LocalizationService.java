package vg.rg.frontend.vaadin.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions;
import vg.rg.frontend.vaadin.component.datetime.DateTimes;
import vg.rg.frontend.vaadin.component.datetime.TemporalPickers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * TODO implement real logic
 * Expectation are
 *  1. Correct handling user timezone
 *  2. Correct date time formatting (??? based on explicit user choice)
 */
@Service
@Slf4j
public class LocalizationService implements I18NProvider {

    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("uk-UA");
    public static final Locale ENGLISH_LOCALE = Locale.ENGLISH;
    private static final String MISSING_KEY = "i18n.missing";
    private static final String TERMINAL_FALLBACK = "Переклад тимчасово недоступний";
    private static final List<Locale> PROVIDED_LOCALES = List.of(
            DEFAULT_LOCALE,
            ENGLISH_LOCALE
    );

    private final MessageSource messageSource;

    public LocalizationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public DateTimePicker newDateTimePicker(String label, DateDisplayOptions options) {
        return TemporalPickers.newDateTimePicker(this, label, options);
    }

    public void setValue(DateTimePicker dateTimePicker, Instant value) {
        dateTimePicker.setValue(toLocalDateTime(value));
    }

    public Instant getInstant(DateTimePicker dateTimePicker) {
        return dateTimePicker
                .getOptionalValue()
                .map(v -> v.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    public String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }

        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(currentLocale())
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    /**
     * Formats an event {@link Instant} through the shared {@code component.datetime} formatter, with this
     * service as the locale bridge.
     *
     * <p>Distinct from {@link #formatDateTime(Instant)}, which keeps its own {@link FormatStyle}/system-
     * zone behaviour: this one delegates to {@link DateTimes#format} so a caption and the temporal
     * pickers share one presentation, and it reads the event zone the same way the event view does —
     * event instants have no assigned zone and are treated as UTC. The caller passes the same
     * {@link DateDisplayOptions} it builds its pickers with, so the weekday/year/seconds choices match.
     */
    public String formatEventDateTime(Instant instant, DateDisplayOptions options) {
        if (instant == null) {
            return "";
        }
        return DateTimes.format(DateTimes.toLocal(instant, ZoneOffset.UTC), currentLocale(), options);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        //TODO implement real logic
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public String i18n(Exception e) {
        var simpleName = "exception." + e.getClass().getSimpleName();
        var result = i18n(simpleName);
        if (!simpleName.equals(result)) {
            return result;
        }

        log.warn("Cannot localize exception type {}", e.getClass().getSimpleName());
        return i18n("exception.unknown");
    }

    public String i18n(String key) {
        return getTranslation(key, currentLocale());
    }

    public Locale getCurrentLocale() {
        return currentLocale();
    }

    public void setCurrentLocale(Locale locale) {
        var normalizedLocale = normalizeLocale(locale);
        var session = VaadinSession.getCurrent();
        if (null != session) {
            session.setLocale(normalizedLocale);
        }

        var ui = UI.getCurrent();
        if (null != ui && !normalizedLocale.equals(ui.getLocale())) {
            ui.setLocale(normalizedLocale);
        }
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return PROVIDED_LOCALES;
    }

    @Override
    public Locale getDefaultLocale() {
        return DEFAULT_LOCALE;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (null == key || key.isBlank()) {
            return terminalFallback();
        }
        var normalizedLocale = normalizeLocale(locale);
        var translated = messageSource.getMessage(key, params, null, normalizedLocale);
        if ((translated == null || translated.isBlank()) && !DEFAULT_LOCALE.equals(normalizedLocale)) {
            translated = messageSource.getMessage(key, params, null, DEFAULT_LOCALE);
        }
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        log.warn("Missing translation for semantic key {}", key);
        return terminalFallback();
    }

    private Locale currentLocale() {
        var session = VaadinSession.getCurrent();
        if (null != session && session.getLocale() != null) {
            return normalizeLocale(session.getLocale());
        }
        return DEFAULT_LOCALE;
    }

    Locale normalizeLocale(Locale locale) {
        if (ENGLISH_LOCALE.equals(locale)) {
            return ENGLISH_LOCALE;
        }
        return DEFAULT_LOCALE;
    }

    private String terminalFallback() {
        var translated = messageSource.getMessage(MISSING_KEY, null, null, DEFAULT_LOCALE);
        return translated == null || translated.isBlank() ? TERMINAL_FALLBACK : translated;
    }

}
