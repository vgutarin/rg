package vg.rg.frontend.vaadin.component.datetime;

import lombok.Builder;
import lombok.Value;
import java.util.Objects;

/** A closed range; equal endpoints are allowed. An absent range is represented by null. */
@Value
public class TemporalRange<T extends Comparable<? super T>> {
    T start;
    T end;

    @Builder
    private TemporalRange(T start, T end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Range end must not precede start");
        }
    }
}
