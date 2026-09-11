package vg.rg.frontend.vaadin.component.location;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.geo.LocationModel;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.unique.id.model.UniqueId;

import java.util.Objects;
import java.util.stream.Stream;

/** A workspace-scoped, single-value location autocomplete field. */
public class LocationPicker extends ComboBox<LocationModel> {

    private static final int BROWSE_PAGE_SIZE = 20;
    private static final Sort LOCATION_NAME_ORDER = Sort.by(
            Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("uniqueId"));

    private final WorkspaceLocationService locationService;
    private UniqueId workspaceId;

    public LocationPicker(LocalizationService localization, WorkspaceLocationService locationService) {
        this.locationService = locationService;

        setPageSize(BROWSE_PAGE_SIZE);
        setPlaceholder(localization.i18n("location-picker.search.placeholder"));
        setClearButtonVisible(true);
        setAllowCustomValue(false);
        setItemLabelGenerator(LocationModel::getName);
        setWidthFull();
        setItems(this::fetchLocations, this::countLocations);
    }

    /** Sets the only workspace the field may browse. Changing scope clears any prior selection. */
    public void setWorkspaceId(UniqueId workspaceId) {
        var nextWorkspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        if (Objects.equals(this.workspaceId, nextWorkspaceId)) {
            return;
        }
        this.workspaceId = nextWorkspaceId;
        clear();
        getDataProvider().refreshAll();
    }

    /** Restores a persisted location reference for an editor. */
    public void setSelectedLocationId(UniqueId locationId) {
        setValue(locationId == null ? null : locationService.read(locationId));
    }

    private Stream<LocationModel> fetchLocations(Query<LocationModel, String> query) {
        if (workspaceId == null) {
            return Stream.empty();
        }
        var filter = normalizedFilter(query);
        if (filter.isBlank()) {
            return locationService.browse(workspaceId, PageRequest.of(
                    query.getPage(), query.getPageSize(), LOCATION_NAME_ORDER)).getContent().stream();
        }
        return locationService.searchByName(workspaceId, filter, 0).stream()
                .skip(query.getOffset())
                .limit(query.getLimit());
    }

    private int countLocations(Query<LocationModel, String> query) {
        if (workspaceId == null) {
            return 0;
        }
        var filter = normalizedFilter(query);
        if (filter.isBlank()) {
            return Math.toIntExact(locationService.browse(workspaceId,
                    PageRequest.of(0, 1, LOCATION_NAME_ORDER)).getTotalElements());
        }
        return locationService.searchByName(workspaceId, filter, 0).size();
    }

    private static String normalizedFilter(Query<LocationModel, String> query) {
        return query.getFilter().orElse("").trim();
    }
}
