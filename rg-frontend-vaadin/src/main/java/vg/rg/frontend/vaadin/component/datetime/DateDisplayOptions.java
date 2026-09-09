package vg.rg.frontend.vaadin.component.datetime;

import lombok.Builder;
import lombok.Value;
import java.time.Duration;

/** Locale-independent display and entry preferences. Values are never rounded to the step. */
@Value
public class DateDisplayOptions {
    boolean showShortDayName;
    boolean showYear;
    boolean showSeconds;
    Duration step;

    @Builder(toBuilder = true)
    private DateDisplayOptions(boolean showShortDayName, Boolean showYear, boolean showSeconds, Duration step) {
        this.showShortDayName = showShortDayName;
        this.showYear = showYear == null || showYear;
        this.showSeconds = showSeconds;
        this.step = step == null ? Duration.ofMinutes(15) : step;
        if (this.step.isNegative() || this.step.isZero()
                || this.step.compareTo(Duration.ofDays(1)) > 0
                || this.step.getNano() != 0
                || 86400 % this.step.getSeconds() != 0) {
            throw new IllegalArgumentException("Step must be a positive whole-second divisor of a day");
        }
    }
}
