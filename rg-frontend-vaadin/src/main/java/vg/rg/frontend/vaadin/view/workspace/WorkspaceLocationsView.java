package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;
import vg.rg.frontend.vaadin.component.dialog.Dialogs;
import vg.rg.frontend.vaadin.component.dialog.Prompt;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.config.MapsClientProperties;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.service.MapsResolutionBridge;
import vg.rg.frontend.vaadin.view.auth.AccessDeniedErrorView;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.frontend.vaadin.view.auth.TelegramAuthView;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.geo.ProximityMatch;
import vg.rg.model.geo.ProximityQuery;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Locations inside the active workspace, split into two tabs (mobile-first):
 * <ul>
 *   <li><b>View</b> — name search over the workspace's locations; initial state lists the first
 *       {@value #VIEW_PAGE_SIZE}. Selecting an entry opens its detail (edit / delete).</li>
 *   <li><b>Add</b> — the "Add location" button (Google Maps picker). After coordinates are picked, an
 *       inline add form appears (name pre-filled from the picked place) with the list of already
 *       registered locations within the proximity radius (±500 m) below it. Saving hides the form and
 *       the new location appears on top of the nearby list; cancelling just hides the form.</li>
 * </ul>
 *
 * <p>Behaviourally identical to the global locations screen — same tabs, same cards, same inline
 * accordion detail, same styles — with exactly one difference: <strong>every query is confined to the
 * active workspace</strong>, including the proximity suggestion, which therefore never surfaces an
 * identical copy living in another workspace.
 *
 * <p>The other consequence of that scope is where the guards point. Entry is gated on the app-wide
 * workspace permission, and each action is authorized against the resource it acts on — the workspace for
 * create and reads, the location's own identifier for edit and delete — so a workspace owner needs no
 * separate location capability inside their own workspace.
 */
@PageTitle("page.workspace-locations.title")
@Route(value = "workspaces/locations", layout = WorkspaceLayout.class)
@JavaScript(TelegramAuthView.TELEGRAM_JS)
@JsModule("./ts/maps/google-maps-connector.ts")
@PermitAll
public class WorkspaceLocationsView extends VerticalLayout
        implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient AuthenticationContext authenticationContext;
    private final transient WorkspaceLocationService locationService;
    private final transient WorkspaceSelectionService selectionService;
    private final transient MapsResolutionBridge mapsResolutionBridge;
    private final transient MapsClientProperties mapsClientProperties;

    /** Initial number of locations listed on the View tab before any name filter. */
    private static final int VIEW_PAGE_SIZE = 10;

    private final Div content = new Div();
    private final DisclosureList viewList = new DisclosureList();
    private final Div suggestions = new Div();
    private final DisclosureList suggestionList = new DisclosureList();
    private final Div addForm = new Div();
    private final TextField viewSearch = new TextField();

    /** Preserved across re-renders (e.g. language switch) so the selected tab stays selected. */
    private int selectedTabIndex;

    /** The workspace every query on this screen is scoped to, resolved once per navigation. */
    private UniqueId workspaceId;

    private BigDecimal acquiredLatitude;
    private BigDecimal acquiredLongitude;
    private String acquiredPlaceId;

    public WorkspaceLocationsView(LocalizationService localization,
                                  AuthorityChecker authorityChecker,
                                  AuthenticationContext authenticationContext,
                                  WorkspaceLocationService locationService,
                                  WorkspaceSelectionService selectionService,
                                  MapsResolutionBridge mapsResolutionBridge,
                                  MapsClientProperties mapsClientProperties) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.authenticationContext = authenticationContext;
        this.locationService = locationService;
        this.selectionService = selectionService;
        this.mapsResolutionBridge = mapsResolutionBridge;
        this.mapsClientProperties = mapsClientProperties;

        viewSearch.setClearButtonVisible(true);
        viewSearch.setWidthFull();
        viewSearch.setValueChangeMode(ValueChangeMode.LAZY);
        viewSearch.addValueChangeListener(event -> renderViewList(event.getValue()));

        // The accordion shape and its styles come from DisclosureList, shared with the participants
        // screen so the two cannot drift apart.
        suggestions.setWidthFull();

        addClassName("secure-view");
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        content.removeAll();
        if (!authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            event.rerouteTo(hasNoEffectivePermissions() ? NoAccessView.class : AccessDeniedErrorView.class);
            return;
        }
        // Resolved here rather than read from the surrounding layout: the selection service repairs a
        // stale pointer, so this screen cannot open against a workspace that no longer exists.
        workspaceId = selectionService.activeWorkspace().getUniqueId();
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (workspaceId != null && authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
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

    /** Name search within the workspace; blank query lists the first {@link #VIEW_PAGE_SIZE}. */
    private Component viewTab() {
        viewSearch.setPlaceholder(localization.i18n("locations.search.placeholder"));
        var layout = new VerticalLayout(viewSearch, viewList);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        return layout;
    }

    private void renderViewList(String query) {
        viewList.reset();
        var trimmed = query == null ? "" : query.trim();
        List<LocationModel> results = trimmed.isBlank()
                ? locationService.browse(workspaceId, PageRequest.of(0, VIEW_PAGE_SIZE)).getContent()
                : locationService.searchByName(workspaceId, trimmed, 0);
        if (results.isEmpty()) {
            viewList.add(new Paragraph(localization.i18n(
                    trimmed.isBlank() ? "locations.empty" : "locations.search.no-results")));
            return;
        }
        results.forEach(model -> addItem(viewList, model, null));
    }

    // --- Add tab ------------------------------------------------------------------------------------

    /** "Add location" (Google Maps picker), the inline add form, and the advisory nearby list below. */
    private Component addTab() {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();

        if (canCreate()) {
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
            // Scoped: the suggestion never looks outside the active workspace, so an identical copy in
            // another workspace is invisible here.
            var matches = mapsResolutionBridge.resolveAndSuggest(
                    workspaceId, latitude, longitude, placeId);
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
     * (and no nearby list), if the user may add to this workspace.
     */
    @ClientCallable
    public void onMapsUnavailable() {
        if (!canCreate()) {
            Notification.show(localization.i18n("location.maps.unavailable"));
            return;
        }
        rememberCoordinates(null, null, null);
        suggestions.removeAll();
        showAddForm(null);
    }

    /** Inline add form: name (pre-filled) + description + save/cancel. */
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
        // Stacked full-width on mobile; side-by-side on wider screens (see .form-actions CSS).
        var actions = new Div(save, cancel);
        actions.addClassName("form-actions");
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
            // The workspace comes from the operation, never from the model.
            locationService.create(workspaceId, model);
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
        suggestionList.reset();
        if (matches.isEmpty()) {
            suggestions.add(new Paragraph(localization.i18n("location.no-suggestion")));
            return;
        }
        suggestions.add(new H2(localization.i18n("location.suggestions.title")));
        suggestions.add(suggestionList);
        matches.forEach(match -> {
            // Picking an existing suggestion expands its detail inline (no new location created).
            var distance = localization.getTranslation("location.distance-meters",
                    localization.getCurrentLocale(), Math.round(match.distanceMeters()));
            addItem(suggestionList, match.location(), distance);
        });
    }

    /**
     * Appends one location to the given accordion. The header shape, the chevron and the single-open
     * behaviour all come from {@link DisclosureList}; this method only decides what goes in the panel.
     */
    private void addItem(DisclosureList list, LocationModel model, String meta) {
        // The location's identifier is the entry's key: it is what keeps an expanded row expanded across
        // the re-render that follows an edit, so the user sees their change in the panel they were
        // already reading rather than having to find and reopen the row.
        fillDetailPanel(list.addItem(model.getUniqueId(), model.getName(), meta), model);
    }

    /**
     * The collapsible detail panel shown beneath a list entry: description, coordinate/place-id info
     * rows, a primary "open in Google Maps" action (when coordinates exist), and — subject to
     * authority — edit and delete actions. An inner wrapper lets the panel animate its height open.
     */
    private void fillDetailPanel(Div inner, LocationModel model) {
        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            var description = new Paragraph(model.getDescription());
            description.addClassName(DisclosureList.DETAIL_DESCRIPTION);
            inner.add(description);
        }

        // Coordinates and place id are optional (a location may be saved without them when Google Maps
        // is unavailable); each is shown as an icon-led info row only when present.
        var hasCoordinates = model.getLatitude() != null && model.getLongitude() != null;
        var info = new Div();
        info.addClassName(DisclosureList.DETAIL_META);
        if (hasCoordinates) {
            info.add(DisclosureList.detailRow(
                    VaadinIcon.MAP_MARKER, localization.i18n("location.field.coordinates"),
                    model.getLatitude().toPlainString() + ", " + model.getLongitude().toPlainString(), false));
        }
        if (model.getGooglePlaceId() != null && !model.getGooglePlaceId().isBlank()) {
            info.add(DisclosureList.detailRow(
                    VaadinIcon.INFO_CIRCLE, localization.i18n("location.place-id"),
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
        // delete is confirmed first. The row hides itself when the user may do neither. Each check
        // addresses the location's own identifier, which resolves to its workspace.
        var actions = new Div();
        actions.addClassName(DisclosureList.ROW_ACTIONS);
        if (authorityChecker.hasAuthority(model.getUniqueId(), LocalPermissions.Location.UPDATE)) {
            var edit = new Button(localization.i18n("location.form.edit.title"), VaadinIcon.EDIT.create(),
                    event -> LocationFormDialog.forEdit(localization, locationService, model,
                            this::afterChange).open());
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            actions.add(edit);
        }
        if (authorityChecker.hasAuthority(model.getUniqueId(), LocalPermissions.Location.DELETE)) {
            var delete = new Button(localization.i18n("location.delete"), VaadinIcon.TRASH.create(),
                    event -> confirmDelete(model));
            delete.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            actions.add(delete);
        }
        if (actions.getElement().getChildCount() > 0) {
            inner.add(actions);
        }
    }

    /**
     * Removal, confirmed first. The shape — including the guarantee that a double press deletes once —
     * belongs to {@link Dialogs#confirmDeletion}, so all three screens that delete something behave
     * identically by construction rather than by three people remembering to.
     *
     * @return the opened confirmation dialog and its buttons, so the flow can be driven directly
     */
    Prompt confirmDelete(LocationModel model) {
        return Dialogs.confirmDeletion(localization,
                "location.delete", "location.delete.confirm", "location.delete",
                "location.cancel", () -> delete(model));
    }

    private void delete(LocationModel model) {
        try {
            locationService.delete(model.getUniqueId());
            // The list re-renders, which removes the deleted entry and its inline panel.
            afterChange();
        } catch (RuntimeException exception) {
            Notification.show(localization.i18n(exception));
        }
    }

    private void afterChange() {
        renderViewList(viewSearch.getValue());
    }

    private void afterCreate() {
        // Refresh the nearby suggestions (when coordinates were acquired) and the View-tab list so the
        // newly created location appears — at ~0 m it sorts to the top of the nearby list.
        if (acquiredLatitude != null && acquiredLongitude != null) {
            renderSuggestions(locationService.findNearby(workspaceId,
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

    /** May the caller add a location to the active workspace? Addresses the workspace, per the create verb. */
    private boolean canCreate() {
        return authorityChecker.hasAuthority(workspaceId, LocalPermissions.Location.CREATE);
    }

    private boolean hasNoEffectivePermissions() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .filter(principal -> principal.userUniqueId() == null
                        || Permissions.recognized(principal.permissions()).isEmpty())
                .isPresent();
    }
}
