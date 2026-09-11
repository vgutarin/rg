package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.config.MapsClientProperties;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.service.MapsResolutionBridge;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@code LocationsViewTest}: the same tabs, the same collapsed cards with a disclosure chevron,
 * the same single-open accordion, and the same detail panel.
 *
 * <p>What these tests additionally pin is the one difference — every read is scoped to the active
 * workspace — and its consequence for the guards: a workspace owner sees the management actions without
 * holding any {@code location:*} capability of their own.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceLocationsViewTest {

    private static final UniqueId WORKSPACE = new UniqueId(5001L);
    private static final UniqueId LOCATION = new UniqueId(6001L);

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock AuthenticationContext authenticationContext;
    @Mock WorkspaceLocationService locationService;
    @Mock WorkspaceSelectionService selectionService;
    @Mock MapsResolutionBridge mapsResolutionBridge;
    @Mock MapsClientProperties mapsClientProperties;
    @Mock BeforeEnterEvent event;

    private WorkspaceLocationsView view() {
        return new WorkspaceLocationsView(localization, authorityChecker, authenticationContext,
                locationService, selectionService, mapsResolutionBridge, mapsClientProperties);
    }

    @Test
    void beforeEnter_missingWorkspacePermission_withUnrecognizedPermission_reroutesToNoAccess() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of("unknown:view"))));

        view().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    @Test
    void beforeEnter_missingWorkspacePermission_noEffectivePermissions_reroutesToNoAccess() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of())));

        view().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    @Test
    void beforeEnter_rendersTwoTabs() {
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE)).thenReturn(true);
        var view = entered();

        var tabs = descendants(view).stream()
                .filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();

        assertThat(tabContents(tabs)).hasSize(2);
        assertThat(descendants(view).stream().anyMatch(H1.class::isInstance)).isTrue();
    }

    @Test
    void beforeEnter_readsAreScopedToTheActiveWorkspace() {
        // The single difference from the global screen: the workspace is passed to every read.
        var view = entered(model("Depot"));

        verify(locationService).browse(eq(WORKSPACE), eq(PageRequest.of(0, 20, locationNameOrder())));
        assertThat(names(view)).contains("Depot");
    }

    @Test
    void search_isScopedToTheActiveWorkspace() {
        var view = entered();
        when(locationService.searchByName(eq(WORKSPACE), anyString(), anyInt()))
                .thenReturn(List.of(model("Found")));

        var search = descendants(view).stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast)
                .findFirst().orElseThrow();
        search.setValue("dep");

        verify(locationService).searchByName(eq(WORKSPACE), eq("dep"), anyInt());
    }

    @Test
    void emptyWorkspace_showsTheEmptyState() {
        var view = entered();

        assertThat(descendants(view).stream()
                .filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText))
                .contains("locations.empty");
    }

    @Test
    void locationRow_rendersCollapsed_withADisclosureChevron() {
        var view = entered(model("Depot"));

        var item = onlyItem(view);
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(chevronIcons(item)).hasSize(1);
    }

    @Test
    void clickingRow_expandsInlinePanel_andClickingAgainCollapsesIt() {
        var view = entered(model("Depot"));
        var item = onlyItem(view);

        click(header(item));
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isTrue();

        click(header(item));
        assertThat(item.hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
    }

    @Test
    void openingAnotherRow_collapsesThePreviouslyOpenOne() {
        var view = entered(model("First"), model("Second"));
        var items = items(view);
        assertThat(items).hasSize(2);

        click(header(items.get(0)));
        click(header(items.get(1)));

        assertThat(items.get(0).hasClassName(DisclosureList.ITEM_OPEN)).isFalse();
        assertThat(items.get(1).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
    }

    /**
     * Saving an edit re-queries and rebuilds the list, so the expanded row is a different element
     * afterwards. It has to stay expanded, showing the updated values — otherwise the panel the user was
     * reading collapses and they have to find and reopen the row to see whether their edit took. The
     * mechanism is {@link DisclosureList}'s entry key; this pins that this screen supplies one.
     */
    @Test
    void reRendering_keepsTheExpandedRowExpanded_withItsUpdatedContent() {
        var view = entered(model("Depot"));
        click(header(onlyItem(view)));
        assertThat(onlyItem(view).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();

        // The re-query an edit triggers: same location, new name.
        when(locationService.browse(eq(WORKSPACE), any())).thenReturn(new PageImpl<>(List.of(
                LocationModel.builder().uniqueId(LOCATION).name("Depot North").build())));
        view.beforeEnter(event);

        assertThat(names(view)).containsExactly("Depot North");
        assertThat(onlyItem(view).hasClassName(DisclosureList.ITEM_OPEN)).isTrue();
    }

    // --- removal, mirroring WorkspaceParticipantsViewTest ------------------------------------------

    @Test
    void removal_isConfirmedFirst() {
        var view = entered(model("Depot"));

        view.confirmDelete(model("Depot"));

        verify(locationService, never()).delete(any());
    }

    /**
     * The guard this screen was missing: it had neither the flag nor a disabled button, so a double tap
     * issued two deletes. Its confirmation returned {@code void}, which is why nothing noticed.
     */
    @Test
    void removal_doubleTap_deletesOnce() {
        var view = entered(model("Depot"));
        var prompt = view.confirmDelete(model("Depot"));

        click(prompt.confirm());
        click(prompt.confirm());

        verify(locationService, times(1)).delete(LOCATION);
    }

    @Test
    void removal_cancel_deletesNothing() {
        var view = entered(model("Depot"));
        var prompt = view.confirmDelete(model("Depot"));

        click(prompt.cancel());

        verify(locationService, never()).delete(any());
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    /** The destructive action has to look destructive, as it does on the other two screens. */
    @Test
    void removal_confirmButtonIsStyledAsTheDestructiveAction() {
        var view = entered(model("Depot"));

        var prompt = view.confirmDelete(model("Depot"));

        assertThat(prompt.confirm().getThemeNames()).contains("primary", "error");
    }

    @Test
    void detailPanel_forAWorkspaceOwner_showsDetailsMapsAndActions() {
        // The owner holds no location:* capability. The scoped check still admits them, because owning
        // the workspace grants complete authority over its contents -- so the actions must be offered.
        lenient().when(authorityChecker.hasAuthority(any(UniqueId.class), anyString())).thenReturn(true);
        var view = entered(withCoordinates());

        var item = onlyItem(view);
        var texts = descendants(item).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText).toList();

        assertThat(texts).contains("location.open-in-maps", "location.form.edit.title", "location.delete");
        assertThat(descendants(item).stream()
                .filter(Span.class::isInstance).map(Span.class::cast)
                .map(Span::getText))
                .contains("location.field.coordinates");
    }

    @Test
    void detailPanel_whenTheScopedCheckDenies_hidesEditAndDelete() {
        lenient().when(authorityChecker.hasAuthority(any(UniqueId.class), anyString())).thenReturn(false);
        var view = entered(withCoordinates());

        var texts = descendants(onlyItem(view)).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText).toList();

        assertThat(texts).doesNotContain("location.form.edit.title", "location.delete");
        // The maps action is derived from coordinates, not from authority, so it stays.
        assertThat(texts).contains("location.open-in-maps");
    }

    @Test
    void withoutCreatePermission_rendersBrowseDirectlyWithoutTabCaptions() {
        lenient().when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE))
                .thenReturn(false);
        var view = entered();

        assertThat(descendants(view).stream().filter(TabSheet.class::isInstance)).isEmpty();
        assertThat(descendants(view).stream()
                .filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText))
                .doesNotContain("locations.add.no-permission");
    }

    @Test
    void withCreatePermission_rendersAddTabWithoutASeparateAddButton() {
        lenient().when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE))
                .thenReturn(true);
        var view = entered();

        assertThat(descendants(view).stream().filter(TabSheet.class::isInstance)).hasSize(1);
        assertThat(descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText))
                .doesNotContain("locations.add");
    }

    @Test
    void savingNewLocation_switchesToBrowseFiltersBySavedNameAndShowsItsRow() {
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE)).thenReturn(true);
        var created = model("New depot");
        when(locationService.create(eq(WORKSPACE), any())).thenReturn(created);
        when(locationService.searchByName(eq(WORKSPACE), eq("New depot"), anyInt()))
                .thenReturn(List.of(created));
        var view = entered();
        view.onMapsUnavailable();

        descendants(view).stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> "location.field.name".equals(field.getLabel()))
                .findFirst().orElseThrow()
                .setValue("New depot");
        var save = descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "location.save".equals(button.getText()))
                .findFirst().orElseThrow();
        click(save);

        var tabs = descendants(view).stream()
                .filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();
        assertThat(tabs.getSelectedIndex()).isZero();
        assertThat(names(tabContents(tabs).getFirst())).containsExactly("New depot");
        verify(locationService).searchByName(WORKSPACE, "New depot", 0);
    }

    // ---------------------------------------------------------------------------------- fixtures

    /**
     * A dialog auto-adds itself to the current UI when opened, so the removal flow needs one.
     *
     * <p>Held in a field on purpose: Vaadin keeps the current UI behind a weak reference, so one with no
     * other referent can be collected mid-test and {@code dialog.open()} then fails intermittently with
     * "No currently active UI found". See {@code specs/current/engineering-notes.md}.
     */
    private com.vaadin.flow.component.UI ui;

    @org.junit.jupiter.api.BeforeEach
    void attachUi() {
        ui = new com.vaadin.flow.component.UI();
        com.vaadin.flow.component.UI.setCurrent(ui);
    }

    @org.junit.jupiter.api.AfterEach
    void detachUi() {
        com.vaadin.flow.component.UI.setCurrent(null);
        ui = null;
    }

    @org.junit.jupiter.api.BeforeEach
    void resetIdentifiers() {
        IDS.clear();
    }

    private WorkspaceLocationsView entered(LocationModel... models) {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).build());
        when(locationService.browse(eq(WORKSPACE), any())).thenReturn(new PageImpl<>(List.of(models)));
        lenient().when(mapsClientProperties.browserApiKey()).thenReturn("key");
        lenient().when(mapsClientProperties.mapId()).thenReturn("map");

        var view = view();
        view.beforeEnter(event);
        return view;
    }

    /**
     * Distinct identifiers per name, because they key the accordion's open entry: two fixtures sharing
     * one would be indistinguishable to it, and a test asserting that <em>this</em> row stayed open
     * would pass because a different row opened.
     */
    private static final java.util.Map<String, UniqueId> IDS = new java.util.LinkedHashMap<>();

    private static LocationModel model(String name) {
        var id = IDS.computeIfAbsent(name, key -> IDS.isEmpty()
                ? LOCATION
                : new UniqueId(LOCATION.getLongValue() + IDS.size()));
        return LocationModel.builder().uniqueId(id).name(name).build();
    }

    private static LocationModel withCoordinates() {
        return LocationModel.builder()
                .uniqueId(LOCATION)
                .name("Depot")
                .description("Behind the station")
                .latitude(BigDecimal.valueOf(50.0))
                .longitude(BigDecimal.valueOf(30.0))
                .googlePlaceId("ChIJ-place-id")
                .build();
    }

    private static Sort locationNameOrder() {
        return Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("uniqueId"));
    }

    private AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }

    // ------------------------------------------------- traversal helpers, as in LocationsViewTest

    private List<String> names(Component view) {
        return descendants(view).stream()
                .filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.hasClassName(DisclosureList.ROW_NAME))
                .map(Span::getText).toList();
    }

    private List<Div> items(Component view) {
        return descendants(view).stream()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ITEM))
                .toList();
    }

    private Div onlyItem(Component view) {
        var items = items(view);
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private Div header(Div item) {
        return item.getChildren()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName(DisclosureList.ROW))
                .findFirst().orElseThrow();
    }

    private List<String> chevronIcons(Div item) {
        return descendants(item).stream()
                .filter(component -> component.hasClassName(DisclosureList.ROW_ICON))
                .map(component -> component.getElement().getAttribute("icon"))
                .toList();
    }

    private void click(Component element) {
        ComponentUtil.fireEvent(element, new ClickEvent<>(element));
    }

    private List<Component> descendants(Component component) {
        var children = new ArrayList<>(component.getChildren().toList());
        // TabSheet content is not attached without a UI, so getChildren() omits it; pull each tab's
        // content component explicitly so the traversal reaches the tab contents.
        if (component instanceof TabSheet tabSheet) {
            children.addAll(tabContents(tabSheet));
        }
        return children.stream()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
                .toList();
    }

    private List<Component> tabContents(TabSheet tabSheet) {
        var contents = new ArrayList<Component>();
        for (int index = 0; index < 100; index++) {
            Tab tab;
            try {
                tab = tabSheet.getTabAt(index);
            } catch (RuntimeException outOfRange) {
                break;
            }
            if (tab == null) {
                break;
            }
            var content = tabSheet.getComponent(tab);
            if (content != null) {
                contents.add(content);
            }
        }
        return contents;
    }
}
