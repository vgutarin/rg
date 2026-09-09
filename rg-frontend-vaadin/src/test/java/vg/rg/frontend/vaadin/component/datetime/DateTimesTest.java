package vg.rg.frontend.vaadin.component.datetime;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class DateTimesTest {
    private final LocalDate date = LocalDate.of(2026, 9, 9);

    @Test void stepDefaultsAndValidation() {
        assertThat(DateDisplayOptions.builder().build().getStep()).isEqualTo(Duration.ofMinutes(15));
        var options = DateDisplayOptions.builder().showYear(false).step(Duration.ofSeconds(1)).build();
        assertThat(options.toBuilder().build()).isEqualTo(options);
        for (var invalid : java.util.List.of(Duration.ZERO, Duration.ofSeconds(-1),
                Duration.ofMinutes(7), Duration.ofMillis(500), Duration.ofDays(2))) {
            assertThatThrownBy(() -> DateDisplayOptions.builder().step(invalid).build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void optionsAndLocaleControlDisplay() {
        var shortDate = DateDisplayOptions.builder().showYear(false).build();
        assertThat(DateTimes.format(date, Locale.ENGLISH, shortDate)).isEqualTo("Sep 9");
        assertThat(DateTimes.format(date, Locale.ENGLISH, shortDate.toBuilder().showShortDayName(true).build()))
                .isEqualTo("Wed Sep 9");
        assertThat(DateTimes.format(date, Locale.forLanguageTag("uk-UA"), shortDate))
                .contains("9", "вер").doesNotContain("Sep", "2026");
        assertThat(DateTimes.format(date, Locale.ENGLISH, DateDisplayOptions.builder().build())).contains("2026");
        assertThat(DateTimes.format(date.atTime(14, 30, 15), Locale.ENGLISH,
                shortDate.toBuilder().showSeconds(true).build())).contains(":15");
        assertThat(DateTimes.format((LocalDate) null, Locale.ENGLISH, shortDate)).isEmpty();
    }

    @Test void rangesRejectMissingOrReversedEndpointsButAllowEqualOnes() {
        assertThatThrownBy(() -> TemporalRange.<LocalDate>builder().start(date).end(date.minusDays(1)).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemporalRange.<LocalDate>builder().start(date).build())
                .isInstanceOf(NullPointerException.class);
        assertThat(TemporalRange.<LocalDate>builder().start(date).end(date).build().getEnd()).isEqualTo(date);
    }

    @Test void conversionUsesAnExplicitZoneAndRejectsDstGapsAndOverlaps() {
        var zone = ZoneId.of("Europe/Kyiv");
        var instant = Instant.parse("2026-09-09T12:00:00Z");
        assertThat(DateTimes.toLocal(instant, zone)).isEqualTo(date.atTime(15, 0));
        assertThat(DateTimes.toInstant(DateTimes.toLocal(instant, zone), zone)).isEqualTo(instant);
        assertThatThrownBy(() -> DateTimes.toInstant(LocalDateTime.of(2026, 3, 29, 3, 30), zone))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateTimes.toInstant(LocalDateTime.of(2026, 10, 25, 3, 30), zone))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
