package vg.rg.frontend.vaadin.component.location;

import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.geo.LocationModel;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationPickerTest {

    private static final UniqueId WORKSPACE = new UniqueId(6001L);
    private static final UniqueId OTHER_WORKSPACE = new UniqueId(6003L);
    private static final UniqueId LOCATION = new UniqueId(6002L);

    @Mock LocalizationService localization;
    @Mock WorkspaceLocationService locationService;

    @Test
    void blankFilter_usesTheSameTwentyItemAlphabeticalBrowsePageAsLocationsView() {
        arrange();
        when(locationService.browse(eq(WORKSPACE), any()))
                .thenReturn(new PageImpl<>(List.of(location("Arena"))));
        var picker = picker();
        picker.setWorkspaceId(WORKSPACE);

        assertThat(items(picker, "")).extracting(LocationModel::getName).containsExactly("Arena");

        verify(locationService).browse(eq(WORKSPACE), eq(PageRequest.of(0, 20, locationNameOrder())));
    }

    @Test
    void typedFilter_usesTheConfiguredWorkspaceAndNativeAutocompleteItems() {
        arrange();
        when(locationService.searchByName(eq(WORKSPACE), eq("are"), anyInt()))
                .thenReturn(List.of(location("Arena")));
        var picker = picker();
        picker.setWorkspaceId(WORKSPACE);

        assertThat(items(picker, "are")).extracting(LocationModel::getName).containsExactly("Arena");
        assertThat(picker.isAllowCustomValue()).isFalse();
        assertThat(picker.getItemLabelGenerator().apply(location("Arena"))).isEqualTo("Arena");

        verify(locationService).searchByName(WORKSPACE, "are", 0);
    }

    @Test
    void selectedOption_becomesTheOnlyFieldValue_andCanBeCleared() {
        arrange();
        var arena = location("Arena");
        var picker = picker();

        picker.setValue(arena);
        assertThat(picker.getValue()).isSameAs(arena);

        picker.clear();
        assertThat(picker.getValue()).isNull();
    }

    @Test
    void persistedReference_isRestored_andChangingWorkspaceClearsIt() {
        arrange();
        var arena = location("Arena");
        when(locationService.read(LOCATION)).thenReturn(arena);
        var picker = picker();
        picker.setWorkspaceId(WORKSPACE);

        picker.setSelectedLocationId(LOCATION);
        assertThat(picker.getValue()).isSameAs(arena);

        picker.setWorkspaceId(OTHER_WORKSPACE);
        assertThat(picker.getValue()).isNull();
    }

    private LocationPicker picker() {
        return new LocationPicker(localization, locationService);
    }

    private void arrange() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static List<LocationModel> items(LocationPicker picker, String filter) {
        return locationProvider(picker).fetch(new Query<>(0, 20, List.of(), null, filter)).toList();
    }

    @SuppressWarnings("unchecked")
    private static DataProvider<LocationModel, String> locationProvider(LocationPicker picker) {
        return (DataProvider<LocationModel, String>) picker.getDataProvider();
    }

    private static LocationModel location(String name) {
        return LocationModel.builder().uniqueId(LOCATION).name(name).description("Indoor court").build();
    }

    private static Sort locationNameOrder() {
        return Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("uniqueId"));
    }
}
