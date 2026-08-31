package vg.rg.frontend.vaadin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.model.ProximityQuery;
import vg.rg.service.LocationService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapsResolutionBridgeTest {

    @Mock LocationService locationService;

    private MapsResolutionBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new MapsResolutionBridge(locationService);
    }

    @Test
    void validate_withPlaceId_returnsCoordinatesAndPlaceId() {
        var result = bridge.validate(50.0, 30.0, "ChIJ-place-id");

        assertThat(result.latitude().doubleValue()).isEqualTo(50.0);
        assertThat(result.longitude().doubleValue()).isEqualTo(30.0);
        assertThat(result.googlePlaceId()).isEqualTo("ChIJ-place-id");
    }

    @Test
    void validate_placeIdAbsentOrBlank_normalizesToNull() {
        assertThat(bridge.validate(50.0, 30.0, null).googlePlaceId()).isNull();
        assertThat(bridge.validate(50.0, 30.0, "   ").googlePlaceId()).isNull();
    }

    @Test
    void validate_outOfRangeLatitude_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> bridge.validate(91.0, 30.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_outOfRangeLongitude_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> bridge.validate(50.0, 181.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_missingCoordinates_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> bridge.validate(null, 30.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_oversizedPlaceId_throwsIllegalArgumentException() {
        var oversized = "x".repeat(MapsResolutionBridge.MAX_PLACE_ID_LENGTH + 1);

        assertThatThrownBy(() -> bridge.validate(50.0, 30.0, oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveAndSuggest_validPayload_queriesNearbyWithAcquiredCoordinates() {
        when(locationService.findNearby(any())).thenReturn(List.of());

        bridge.resolveAndSuggest(50.0, 30.0, "ChIJ-place-id");

        verify(locationService).findNearby(argThatCoordinates(50.0, 30.0));
    }

    @Test
    void resolveAndSuggest_invalidPayload_throwsBeforeQuerying() {
        assertThatThrownBy(() -> bridge.resolveAndSuggest(91.0, 30.0, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(locationService, never()).findNearby(any());
    }

    private static ProximityQuery argThatCoordinates(double latitude, double longitude) {
        return org.mockito.ArgumentMatchers.argThat(query ->
                query.latitude().doubleValue() == latitude
                        && query.longitude().doubleValue() == longitude);
    }
}
