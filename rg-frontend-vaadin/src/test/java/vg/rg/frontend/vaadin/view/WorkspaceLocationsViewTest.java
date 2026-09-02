package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasStyle;
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
import vg.rg.frontend.vaadin.config.MapsClientProperties;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.service.MapsResolutionBridge;
import vg.rg.model.LocationModel;
import vg.rg.model.WorkspaceModel;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.rg.service.WorkspaceLocationService;
import vg.rg.service.WorkspaceSelectionService;
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
    void beforeEnter_missingWorkspacePermission_withOtherPermissions_reroutesToAccessDenied() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Reports.READ))));

        view().beforeEnter(event);

        verify(event).rerouteTo(AccessDeniedErrorView.class);
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

        verify(locationService).browse(eq(WORKSPACE), any());
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
        assertThat(item.hasClassName("location-item--open")).isFalse();
        assertThat(chevronIcons(item)).hasSize(1);
    }

    @Test
    void clickingRow_expandsInlinePanel_andClickingAgainCollapsesIt() {
        var view = entered(model("Depot"));
        var item = onlyItem(view);

        click(header(item));
        assertThat(item.hasClassName("location-item--open")).isTrue();

        click(header(item));
        assertThat(item.hasClassName("location-item--open")).isFalse();
    }

    @Test
    void openingAnotherRow_collapsesThePreviouslyOpenOne() {
        var view = entered(model("First"), model("Second"));
        var items = items(view);
        assertThat(items).hasSize(2);

        click(header(items.get(0)));
        click(header(items.get(1)));

        assertThat(items.get(0).hasClassName("location-item--open")).isFalse();
        assertThat(items.get(1).hasClassName("location-item--open")).isTrue();
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
    void addTab_whenCreateIsDenied_explainsInsteadOfOfferingTheButton() {
        lenient().when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE))
                .thenReturn(false);
        var view = entered();

        assertThat(descendants(view).stream()
                .filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .map(Paragraph::getText))
                .contains("locations.add.no-permission");
    }

    @Test
    void addTab_whenCreateIsAllowed_offersTheAddButton() {
        lenient().when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.CREATE))
                .thenReturn(true);
        var view = entered();

        assertThat(descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText))
                .contains("locations.add");
    }

    // ---------------------------------------------------------------------------------- fixtures

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

    private static LocationModel model(String name) {
        return LocationModel.builder().uniqueId(LOCATION).name(name).build();
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

    private AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }

    // ------------------------------------------------- traversal helpers, as in LocationsViewTest

    private List<String> names(Component view) {
        return descendants(view).stream()
                .filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.hasClassName("location-row__name"))
                .map(Span::getText).toList();
    }

    private List<Div> items(Component view) {
        return descendants(view).stream()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName("location-item"))
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
                .filter(div -> div.hasClassName("location-row"))
                .findFirst().orElseThrow();
    }

    private List<String> chevronIcons(Div item) {
        return descendants(item).stream()
                .filter(component -> component instanceof HasStyle style
                        && style.hasClassName("location-row__icon"))
                .map(component -> component.getElement().getAttribute("icon"))
                .toList();
    }

    private void click(Div element) {
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
