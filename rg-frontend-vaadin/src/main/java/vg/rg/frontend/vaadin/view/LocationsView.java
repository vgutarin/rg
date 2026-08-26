package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springframework.data.domain.PageRequest;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.MainView;
import vg.rg.frontend.vaadin.config.MapsClientProperties;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.service.MapsResolutionBridge;
import vg.rg.frontend.vaadin.telegram.TelegramAuthView;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityMatch;
import vg.rg.model.ProximityQuery;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.Permissions;
import vg.rg.service.LocationService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Locations screen, split into two tabs (mobile-first):
 * <ul>
 *   <li><b>View</b> — name search over the shared collection; initial state lists the first
 *       {@value #VIEW_PAGE_SIZE} locations. Selecting an entry opens its detail (edit / delete).</li>
 *   <li><b>Add</b> — the "Add location" button (Google Maps picker). After coordinates are picked, an
 *       inline add form appears (name pre-filled from the picked place) with the list of already
 *       registered locations within the proximity radius (±500 m) below it. Saving hides the form and
 *       the new location appears on top of the nearby list; cancelling just hides the form.</li>
 * </ul>
 */
@PageTitle("page.locations.title")
@Route(value = "locations", layout = MainView.class)
@JavaScript(TelegramAuthView.TELEGRAM_JS)
@JsModule("./google-maps-connector.ts")
@PermitAll
public class LocationsView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient AuthenticationContext authenticationContext;
    private final transient LocationService locationService;
    private final transient MapsResolutionBridge mapsResolutionBridge;
    private final transient MapsClientProperties mapsClientProperties;

    /** Initial number of locations listed on the View tab before any name filter. */
    private static final int VIEW_PAGE_SIZE = 10;

    private final Div content = new Div();
    private final Div viewList = new Div();
    private final Div suggestions = new Div();
    private final Div addForm = new Div();
    private final TextField viewSearch = new TextField();

    /** Preserved across re-renders (e.g. language switch) so the selected tab stays selected. */
    private int selectedTabIndex;

    /** The list entry whose inline detail panel is currently expanded (single-open), or {@code null}. */
    private Div expandedItem;

    private BigDecimal acquiredLatitude;
    private BigDecimal acquiredLongitude;
    private String acquiredPlaceId;

    public LocationsView(LocalizationService localization,
                         AuthorityChecker authorityChecker,
                         AuthenticationContext authenticationContext,
                         LocationService locationService,
                         MapsResolutionBridge mapsResolutionBridge,
                         MapsClientProperties mapsClientProperties) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.authenticationContext = authenticationContext;
        this.locationService = locationService;
        this.mapsResolutionBridge = mapsResolutionBridge;
        this.mapsClientProperties = mapsClientProperties;

        viewSearch.setClearButtonVisible(true);
        viewSearch.setWidthFull();
        viewSearch.setValueChangeMode(ValueChangeMode.LAZY);
        viewSearch.addValueChangeListener(event -> renderViewList(event.getValue()));

        // Full-width list containers so the rows span the whole width (see .location-list CSS).
        viewList.addClassName("location-list");
        suggestions.addClassName("location-list");

        addClassName("secure-view");
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        content.removeAll();
        if (!authorityChecker.hasAuthority(Permissions.Location.VIEW)) {
            event.rerouteTo(hasNoEffectivePermissions() ? NoAccessView.class : AccessDeniedErrorView.class);
            return;
        }
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (authorityChecker.hasAuthority(Permissions.Location.VIEW)) {
            render();
        } else {
            content.removeAll();
        }
    }

    private void render() {
        content.removeAll();
        suggestions.removeAll();
        hideAddForm();
        content.setWidthFull();
        content.addClassNames("semantic-card", "aura-surface");

        content.add(new H1(localization.i18n("locations.title")));

        var tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(localization.i18n("locations.tab.view"), viewTab());
        tabs.add(localization.i18n("locations.tab.add"), addTab());
        tabs.setSelectedIndex(Math.min(selectedTabIndex, 1));
        // Remember the selection so a language switch (which re-renders) keeps the same tab.
        tabs.addSelectedChangeListener(event -> selectedTabIndex = tabs.getSelectedIndex());
        content.add(tabs);

        renderViewList(viewSearch.getValue());
    }

    // --- View tab -----------------------------------------------------------------------------------

    /** Name search over the shared collection; blank query lists the first {@link #VIEW_PAGE_SIZE}. */
    private Component viewTab() {
        viewSearch.setPlaceholder(localization.i18n("locations.search.placeholder"));
        var layout = new VerticalLayout(viewSearch, viewList);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        return layout;
    }

    private void renderViewList(String query) {
        viewList.removeAll();
        expandedItem = null;
        var trimmed = query == null ? "" : query.trim();
        List<LocationModel> results = trimmed.isBlank()
                ? locationService.browse(PageRequest.of(0, VIEW_PAGE_SIZE)).getContent()
                : locationService.searchByName(trimmed, 0);
        if (results.isEmpty()) {
            viewList.add(new Paragraph(localization.i18n(
                    trimmed.isBlank() ? "locations.empty" : "locations.search.no-results")));
            return;
        }
        results.forEach(model -> viewList.add(locationCard(model)));
    }

    // --- Add tab ------------------------------------------------------------------------------------

    /** "Add location" (Google Maps picker), the inline add form, and the advisory nearby list below. */
    private Component addTab() {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();

        if (authorityChecker.hasAuthority(Permissions.Location.ADD)) {
            var addLocation = new Button(localization.i18n("locations.add"),
                    event -> startCoordinateAcquisition());
            addLocation.setWidthFull();
            layout.add(addLocation, new Paragraph(localization.i18n("locations.add.hint")));
        } else {
            layout.add(new Paragraph(localization.i18n("locations.add.no-permission")));
        }
        layout.add(addForm, suggestions);
        return layout;
    }

    private void startCoordinateAcquisition() {
        // Google Maps picker only. When Maps is unavailable there is no coordinate fallback: the
        // connector signals onMapsUnavailable and the user adds a location without coordinates.
        getElement().executeJs(
                "window.rgInitGoogleMapsConnector($0, $1, $2, $3, $4, $5)",
                getElement(),
                mapsClientProperties.browserApiKey(),
                mapsClientProperties.mapId(),
                localization.i18n("location.picker.confirm"),
                localization.i18n("location.picker.prompt"),
                localization.i18n("location.picker.close"));
    }

    /** Coordinates picked from the map: show nearby suggestions and the inline add form (name pre-filled). */
    @ClientCallable
    public void onCoordinatesAcquired(Double latitude, Double longitude, String placeId, String name) {
        try {
            var matches = mapsResolutionBridge.resolveAndSuggest(latitude, longitude, placeId);
            rememberCoordinates(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude), placeId);
            renderSuggestions(matches);
            showAddForm(name);
            addForm.getElement().executeJs("this.scrollIntoView({behavior:'smooth',block:'center'})");
        } catch (IllegalArgumentException exception) {
            Notification.show(localization.i18n("validation.location.coordinates-invalid"));
        }
    }

    /**
     * Google Maps is unavailable — no coordinate fallback. Show the inline add form with no coordinates
     * (and no nearby list), if the user has add permission.
     */
    @ClientCallable
    public void onMapsUnavailable() {
        if (!authorityChecker.hasAuthority(Permissions.Location.ADD)) {
            Notification.show(localization.i18n("location.maps.unavailable"));
            return;
        }
        rememberCoordinates(null, null, null);
        suggestions.removeAll();
        showAddForm(null);
    }

    /** Inline add form (replaces the former dialog): name (pre-filled) + description + save/cancel. */
    private void showAddForm(String prefillName) {
        addForm.removeAll();
        addForm.addClassName("semantic-card");

        // Read-only coordinates at the very top (only when coordinates were acquired).
        TextField coordinates = null;
        if (acquiredLatitude != null && acquiredLongitude != null) {
            coordinates = new TextField(localization.i18n("location.field.coordinates"));
            coordinates.setValue(acquiredLatitude.toPlainString()
                    + ", " + acquiredLongitude.toPlainString());
            coordinates.setReadOnly(true);
            coordinates.setWidthFull();
        }

        var name = new TextField(localization.i18n("location.field.name"));
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();
        name.setValue(prefillName == null ? "" : prefillName);

        var description = new TextArea(localization.i18n("location.field.description"));
        description.setWidthFull();

        var guidance = new Paragraph(localization.i18n("location.pii-guidance"));

        var save = new Button(localization.i18n("location.save"),
                event -> saveNewLocation(name, description));
        save.setWidthFull();
        var cancel = new Button(localization.i18n("location.cancel"), event -> hideAddForm());
        cancel.setWidthFull();
        // Stacked full-width on mobile; side-by-side on wider screens (see .location-form__actions CSS).
        var actions = new Div(save, cancel);
        actions.addClassName("location-form__actions");
        actions.setWidthFull();

        var form = new VerticalLayout();
        form.setPadding(false);
        form.setSpacing(false);
        form.setWidthFull();
        if (coordinates != null) {
            form.add(coordinates);
        }
        form.add(name, description, guidance, actions);
        addForm.add(form);
        addForm.setVisible(true);
    }

    private void hideAddForm() {
        addForm.removeAll();
        addForm.setVisible(false);
    }

    private void saveNewLocation(TextField name, TextArea description) {
        if (name.getValue() == null || name.getValue().isBlank()) {
            name.setInvalid(true);
            name.setErrorMessage(localization.i18n("validation.location.name-required"));
            return;
        }
        name.setInvalid(false);
        var model = LocationModel.builder()
                .name(name.getValue().trim())
                .description(blankToNull(description.getValue()))
                .latitude(acquiredLatitude)
                .longitude(acquiredLongitude)
                .googlePlaceId(acquiredPlaceId)
                .build();
        try {
            locationService.create(model);
            hideAddForm();
            // Re-render the nearby list (the new location, at ~0 m, sorts to the top) and the View tab.
            afterCreate();
            Notification.show(localization.i18n("location.created"));
        } catch (RuntimeException exception) {
            Notification.show(localization.i18n(exception));
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private void rememberCoordinates(BigDecimal latitude, BigDecimal longitude, String placeId) {
        this.acquiredLatitude = latitude;
        this.acquiredLongitude = longitude;
        this.acquiredPlaceId = (placeId == null || placeId.isBlank()) ? null : placeId;
    }

    /** Advisory nearby list (±500 m), nearest-first. The add affordance is the inline form above. */
    private void renderSuggestions(List<ProximityMatch> matches) {
        suggestions.removeAll();
        expandedItem = null;
        if (matches.isEmpty()) {
            suggestions.add(new Paragraph(localization.i18n("location.no-suggestion")));
            return;
        }
        suggestions.add(new H2(localization.i18n("location.suggestions.title")));
        matches.forEach(match -> suggestions.add(suggestionCard(match)));
    }

    private Div suggestionCard(ProximityMatch match) {
        // Picking an existing suggestion expands its detail inline (no new location created).
        var distance = localization.getTranslation("location.distance-meters",
                localization.getCurrentLocale(), Math.round(match.distanceMeters()));
        return locationItem(match.location(), distance);
    }

    private Div locationCard(LocationModel model) {
        return locationItem(model, null);
    }

    /**
     * A list entry: a clickable header row (name + optional meta + disclosure chevron) with an inline,
     * collapsible detail panel beneath it. Tapping the header expands the panel; tapping again collapses
     * it, and opening one entry collapses whichever was open (single-open, accordion-style).
     */
    private Div locationItem(LocationModel model, String meta) {
        var item = new Div();
        item.addClassName("location-item");

        var header = new Div();
        header.addClassName("location-row");
        header.getElement().setAttribute("role", "button");
        header.getElement().setAttribute("tabindex", "0");

        var text = new Div();
        text.addClassName("location-row__text");
        var title = new Span(model.getName());
        title.addClassName("location-row__name");
        text.add(title);
        if (meta != null) {
            var metaSpan = new Span(meta);
            metaSpan.addClassName("location-row__meta");
            text.add(metaSpan);
        }

        // The chevron points right when collapsed and rotates to point down when open (CSS-driven).
        var icon = VaadinIcon.ANGLE_RIGHT.create();
        icon.addClassName("location-row__icon");
        icon.getElement().setAttribute("aria-hidden", "true");

        header.add(text, icon);
        header.addClickListener(event -> toggleItem(item));

        item.add(header, buildDetailPanel(model));
        return item;
    }

    /** Expand the given entry, collapsing whichever entry was previously open (single-open). */
    private void toggleItem(Div item) {
        if (item.hasClassName("location-item--open")) {
            item.removeClassName("location-item--open");
            expandedItem = null;
            return;
        }
        if (expandedItem != null) {
            expandedItem.removeClassName("location-item--open");
        }
        item.addClassName("location-item--open");
        expandedItem = item;
    }

    /**
     * The collapsible detail panel shown beneath a list entry: description, coordinate/place-id info
     * rows, a primary "open in Google Maps" action (when coordinates exist), and — subject to
     * permissions — edit and delete actions. An inner wrapper lets the panel animate its height open.
     */
    private Div buildDetailPanel(LocationModel model) {
        var panel = new Div();
        panel.addClassName("location-row__panel");
        var inner = new Div();
        inner.addClassName("location-row__panel-inner");
        panel.add(inner);

        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            var description = new Paragraph(model.getDescription());
            description.addClassName("location-detail__description");
            inner.add(description);
        }

        // Coordinates and place id are optional (a location may be saved without them when Google Maps
        // is unavailable); each is shown as an icon-led info row only when present.
        var hasCoordinates = model.getLatitude() != null && model.getLongitude() != null;
        var info = new Div();
        info.addClassName("location-detail__meta");
        if (hasCoordinates) {
            info.add(detailRow(VaadinIcon.MAP_MARKER, localization.i18n("location.field.coordinates"),
                    model.getLatitude().toPlainString() + ", " + model.getLongitude().toPlainString(), false));
        }
        if (model.getGooglePlaceId() != null && !model.getGooglePlaceId().isBlank()) {
            info.add(detailRow(VaadinIcon.INFO_CIRCLE, localization.i18n("location.place-id"),
                    model.getGooglePlaceId(), true));
        }
        if (info.getElement().getChildCount() > 0) {
            inner.add(info);
        }

        // The "open in Google Maps" link is derived from coordinates, so it is only shown when the
        // location has them. It is the primary, full-width call to action. Open via
        // Telegram.WebApp.openLink inside a Mini App (a plain target=_blank anchor does not open in the
        // Telegram webview); fall back to window.open in a normal browser.
        if (hasCoordinates) {
            var openInMaps = new Button(localization.i18n("location.open-in-maps"),
                    VaadinIcon.EXTERNAL_LINK.create(), event ->
                    getElement().executeJs(
                            "const url=$0;const tg=window.Telegram&&window.Telegram.WebApp;"
                                    + "if(tg&&typeof tg.openLink==='function'){tg.openLink(url);}"
                                    + "else{window.open(url,'_blank');}",
                            mapsUrl(model)));
            openInMaps.addThemeVariants(ButtonVariant.PRIMARY);
            openInMaps.addClassName("location-detail__maps");
            inner.add(openInMaps);
        }

        // Management actions live inside the panel, revealed only on intent. Edit opens the form dialog;
        // delete is confirmed first. The row hides itself when the user has neither permission.
        var actions = new Div();
        actions.addClassName("location-row__actions");
        if (authorityChecker.hasAuthority(Permissions.Location.EDIT)) {
            var edit = new Button(localization.i18n("location.form.edit.title"), VaadinIcon.EDIT.create(),
                    event -> LocationFormDialog.forEdit(localization, locationService, model,
                            this::afterChange).open());
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            actions.add(edit);
        }
        if (authorityChecker.hasAuthority(Permissions.Location.DELETE)) {
            var delete = new Button(localization.i18n("location.delete"), VaadinIcon.TRASH.create(),
                    event -> confirmDelete(model));
            delete.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            actions.add(delete);
        }
        if (actions.getElement().getChildCount() > 0) {
            inner.add(actions);
        }

        return panel;
    }

    /**
     * One detail entry: the icon and its label share the first line, the value sits on the line below.
     * {@code monospace} suits opaque ids.
     */
    private Div detailRow(VaadinIcon icon, String label, String value, boolean monospace) {
        var row = new Div();
        row.addClassName("location-detail__row");

        var heading = new Div();
        heading.addClassName("location-detail__heading");
        var glyph = icon.create();
        glyph.addClassName("location-detail__icon");
        glyph.getElement().setAttribute("aria-hidden", "true");
        var labelSpan = new Span(label);
        labelSpan.addClassName("location-detail__label");
        heading.add(glyph, labelSpan);

        var valueSpan = new Span(value);
        valueSpan.addClassName("location-detail__value");
        if (monospace) {
            valueSpan.addClassName("location-detail__value--mono");
        }

        row.add(heading, valueSpan);
        return row;
    }

    private void confirmDelete(LocationModel model) {
        var confirm = new Dialog();
        confirm.setHeaderTitle(localization.i18n("location.delete"));
        confirm.add(new Paragraph(localization.i18n("location.delete.confirm")));
        confirm.getFooter().add(
                new Button(localization.i18n("location.cancel"), event -> confirm.close()),
                new Button(localization.i18n("location.delete"), event -> {
                    try {
                        locationService.delete(model.getUniqueId());
                        confirm.close();
                        // The list re-renders, which removes the deleted entry and its inline panel.
                        afterChange();
                    } catch (RuntimeException exception) {
                        Notification.show(localization.i18n(exception));
                    }
                }));
        confirm.open();
    }

    private void afterChange() {
        renderViewList(viewSearch.getValue());
    }

    private void afterCreate() {
        // Refresh the nearby suggestions (when coordinates were acquired) and the View-tab list so the
        // newly created location appears — at ~0 m it sorts to the top of the nearby list.
        if (acquiredLatitude != null && acquiredLongitude != null) {
            renderSuggestions(locationService.findNearby(
                    new ProximityQuery(acquiredLatitude, acquiredLongitude, null)));
        } else {
            suggestions.removeAll();
        }
        renderViewList(viewSearch.getValue());
    }

    private static String mapsUrl(LocationModel model) {
        var query = model.getLatitude().toPlainString() + "," + model.getLongitude().toPlainString();
        var url = "https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (model.getGooglePlaceId() != null && !model.getGooglePlaceId().isBlank()) {
            url += "&query_place_id=" + URLEncoder.encode(model.getGooglePlaceId(), StandardCharsets.UTF_8);
        }
        return url;
    }

    private boolean hasNoEffectivePermissions() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .filter(principal -> principal.userUniqueId() == null
                        || Permissions.recognized(principal.permissions()).isEmpty())
                .isPresent();
    }
}
