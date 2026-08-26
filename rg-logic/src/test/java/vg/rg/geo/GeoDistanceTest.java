package vg.rg.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoDistanceTest {

    private static final double LAT = 50.0;
    private static final double LNG = 30.0;

    @Test
    void metersBetween_samePoint_isZero() {
        assertThat(GeoDistance.metersBetween(LAT, LNG, LAT, LNG)).isZero();
    }

    @Test
    void metersBetween_knownNorthOffset_matchesExpectedDistance() {
        // ~100 m due north (100 / 111320 degrees of latitude)
        var north = LAT + 100d / 111_320d;

        assertThat(GeoDistance.metersBetween(LAT, LNG, north, LNG)).isCloseTo(100d, within(1d));
    }

    @Test
    void metersBetween_isSymmetric() {
        var a = GeoDistance.metersBetween(LAT, LNG, 51.0, 31.0);
        var b = GeoDistance.metersBetween(51.0, 31.0, LAT, LNG);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void boundingBox_containsRadius_andIsSymmetricAroundPoint() {
        var box = GeoDistance.boundingBox(LAT, LNG, 500);

        assertThat(box.minLat().doubleValue()).isLessThan(LAT);
        assertThat(box.maxLat().doubleValue()).isGreaterThan(LAT);
        assertThat(box.minLng().doubleValue()).isLessThan(LNG);
        assertThat(box.maxLng().doubleValue()).isGreaterThan(LNG);
        // Longitude span widens with latitude (cos factor) and stays >= latitude span.
        var latSpan = box.maxLat().doubleValue() - box.minLat().doubleValue();
        var lngSpan = box.maxLng().doubleValue() - box.minLng().doubleValue();
        assertThat(lngSpan).isGreaterThanOrEqualTo(latSpan);
    }

    @Test
    void boundingBox_clampsToValidCoordinateRange() {
        var box = GeoDistance.boundingBox(89.999, 179.999, 100_000);

        assertThat(box.maxLat().doubleValue()).isLessThanOrEqualTo(90d);
        assertThat(box.maxLng().doubleValue()).isLessThanOrEqualTo(180d);
        assertThat(box.minLat().doubleValue()).isGreaterThanOrEqualTo(-90d);
        assertThat(box.minLng().doubleValue()).isGreaterThanOrEqualTo(-180d);
    }

    @Test
    void boundingBox_nonPositiveRadius_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> GeoDistance.boundingBox(LAT, LNG, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
