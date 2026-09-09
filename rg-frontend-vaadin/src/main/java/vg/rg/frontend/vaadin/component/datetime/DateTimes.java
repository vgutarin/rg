package vg.rg.frontend.vaadin.component.datetime;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Formatting has no dependency on the UI, session, or server's default timezone. */
public final class DateTimes {
    private DateTimes() { }

    public static String format(LocalDate value, Locale locale, DateDisplayOptions options) {
        if (value == null) return "";
        var date = DateTimeFormatter.ofLocalizedPattern(options.isShowYear() ? "yMMMd" : "MMMd")
                .withLocale(locale).format(value);
        return options.isShowShortDayName()
                ? DateTimeFormatter.ofPattern("EEE", locale).format(value) + " " + date : date;
    }

    public static String format(LocalDateTime value, Locale locale, DateDisplayOptions options) {
        if (value == null) return "";
        return format(value.toLocalDate(), locale, options) + " · "
                + DateTimeFormatter.ofLocalizedTime(options.isShowSeconds() ? FormatStyle.MEDIUM : FormatStyle.SHORT)
                .withLocale(locale).format(value);
    }

    public static LocalDateTime toLocal(Instant value, ZoneId zone) {
        return value == null ? null : LocalDateTime.ofInstant(value, zone);
    }

    /** Reject DST gaps and overlaps: callers must resolve ambiguous local times explicitly. */
    public static Instant toInstant(LocalDateTime value, ZoneId zone) {
        if (value == null) return null;
        var offsets = zone.getRules().getValidOffsets(value);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("Local datetime is missing or ambiguous in " + zone);
        }
        return value.toInstant(offsets.getFirst());
    }
}
