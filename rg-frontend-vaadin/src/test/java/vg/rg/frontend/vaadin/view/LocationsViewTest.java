package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
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
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.rg.service.LocationService;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationsViewTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock AuthenticationContext authenticationContext;
    @Mock LocationService locationService;
    @Mock MapsResolutionBridge mapsResolutionBridge;
    @Mock MapsClientProperties mapsClientProperties;
    @Mock BeforeEnterEvent event;

    private LocationsView view() {
        return new LocationsView(localization, authorityChecker, authenticationContext,
                locationService, mapsResolutionBridge, mapsClientProperties);
    }

    @Test
    void beforeEnter_missingViewPermission_withOtherPermissions_reroutesToAccessDenied() {
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of(Permissions.Reports.VIEW))));

        view().beforeEnter(event);

        verify(event).rerouteTo(AccessDeniedErrorView.class);
    }

    @Test
    void beforeEnter_missingViewPermission_noEffectivePermissions_reroutesToNoAccess() {
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(false);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal(Set.of())));

        view().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    @Test
    void beforeEnter_viewPermission_rendersLocationsFromBrowse() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(true);
        when(locationService.browse(any())).thenReturn(new PageImpl<>(List.of(
                LocationModel.builder().name("Central Cafe")
                        .latitude(BigDecimal.valueOf(50.0)).longitude(BigDecimal.valueOf(30.0)).build())));

        var view = view();
        view.beforeEnter(event);

        assertThat(descendants(view)).anyMatch(H1.class::isInstance);
        assertThat(descendants(view)).filteredOn(Span.class::isInstance)
                .extracting(component -> ((Span) component).getText())
                .contains("Central Cafe");
    }

    @Test
    void beforeEnter_viewPermission_emptyCollection_showsEmptyState() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(true);
        when(locationService.browse(any())).thenReturn(new PageImpl<>(List.of()));

        var view = view();
        view.beforeEnter(event);

        assertThat(descendants(view)).filteredOn(Paragraph.class::isInstance)
                .extracting(component -> ((Paragraph) component).getText())
                .contains("locations.empty");
    }

    @Test
    void beforeEnter_rendersTwoTabs() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(true);
        when(locationService.browse(any())).thenReturn(new PageImpl<>(List.of()));

        var view = view();
        view.beforeEnter(event);

        var tabSheet = descendants(view).stream()
                .filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();
        assertThat(tabContents(tabSheet)).hasSize(2);
    }

    @Test
    void beforeEnter_addPermission_addTabShowsAddButton_viewTabShowsSearch() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(true);
        when(authorityChecker.hasAuthority(Permissions.Location.ADD)).thenReturn(true);
        when(locationService.browse(any())).thenReturn(new PageImpl<>(List.of()));

        var view = view();
        view.beforeEnter(event);

        assertThat(descendants(view)).filteredOn(Button.class::isInstance)
                .extracting(component -> ((Button) component).getText())
                .contains("locations.add");
        assertThat(descendants(view)).anyMatch(TextField.class::isInstance);
    }

    @Test
    void locationRow_rendersCollapsed_withADisclosureChevron() {
        var view = renderList(model("Central Cafe"));

        var item = onlyItem(view);
        // Starts collapsed (no open modifier) and carries a right-pointing chevron as the affordance.
        assertThat(item.hasClassName("location-item--open")).isFalse();
        assertThat(chevronIcons(item)).contains("vaadin:angle-right");
    }

    @Test
    void clickingRow_expandsInlinePanel_andClickingAgainCollapsesIt() {
        var view = renderList(model("Central Cafe"));
        var item = onlyItem(view);

        click(header(item));
        assertThat(item.hasClassName("location-item--open")).isTrue();

        click(header(item));
        assertThat(item.hasClassName("location-item--open")).isFalse();
    }

    @Test
    void openingAnotherRow_collapsesThePreviouslyOpenOne() {
        var view = renderList(model("Central Cafe"), model("Riverside Bakery"));
        var items = items(view);
        var first = items.get(0);
        var second = items.get(1);

        click(header(first));
        click(header(second));

        // Single-open (accordion): expanding the second entry collapses the first.
        assertThat(first.hasClassName("location-item--open")).isFalse();
        assertThat(second.hasClassName("location-item--open")).isTrue();
    }

    @Test
    void detailPanel_withManagementPermissions_showsDetailsMapsAndActions() {
        // Lenient: addTab() also probes hasAuthority(ADD); these edit/delete stubs must not make that
        // unmatched call trip strict-stubbing.
        lenient().when(authorityChecker.hasAuthority(Permissions.Location.EDIT)).thenReturn(true);
        lenient().when(authorityChecker.hasAuthority(Permissions.Location.DELETE)).thenReturn(true);
        var view = renderList(LocationModel.builder()
                .name("Central Cafe").description("Quiet corner")
                .latitude(BigDecimal.valueOf(50.0)).longitude(BigDecimal.valueOf(30.0))
                .googlePlaceId("ChIJ123").build());

        assertThat(descendants(view)).filteredOn(Span.class::isInstance)
                .extracting(component -> ((Span) component).getText())
                .contains("50.0, 30.0", "ChIJ123");
        assertThat(descendants(view)).filteredOn(Paragraph.class::isInstance)
                .extracting(component -> ((Paragraph) component).getText())
                .contains("Quiet corner");
        assertThat(descendants(view)).filteredOn(Button.class::isInstance)
                .extracting(component -> ((Button) component).getText())
                .contains("location.open-in-maps", "location.form.edit.title", "location.delete");
    }

    @Test
    void detailPanel_withoutManagementPermissions_hidesEditAndDelete() {
        var view = renderList(LocationModel.builder().name("Central Cafe")
                .latitude(BigDecimal.valueOf(50.0)).longitude(BigDecimal.valueOf(30.0)).build());

        var buttonLabels = descendants(view).stream()
                .filter(Button.class::isInstance).map(component -> ((Button) component).getText()).toList();
        // View-only: the maps link is still offered, but the edit/delete actions are withheld.
        assertThat(buttonLabels).contains("location.open-in-maps");
        assertThat(buttonLabels).doesNotContain("location.form.edit.title", "location.delete");
    }

    private LocationModel model(String name) {
        return LocationModel.builder().name(name)
                .latitude(BigDecimal.valueOf(50.0)).longitude(BigDecimal.valueOf(30.0)).build();
    }

    private LocationsView renderList(LocationModel... models) {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Location.VIEW)).thenReturn(true);
        when(locationService.browse(any())).thenReturn(new PageImpl<>(List.of(models)));
        var view = view();
        view.beforeEnter(event);
        return view;
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

    /** The clickable header row (the toggle) inside a list entry. */
    private Div header(Div item) {
        return item.getChildren()
                .filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.hasClassName("location-row"))
                .findFirst().orElseThrow();
    }

    /** The {@code icon} attribute of each chevron (the header disclosure icon) inside the entry. */
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

    private AuthenticatedUserPrincipal principal(Set<String> permissions) {
        return new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
    }

    private List<Component> descendants(Component component) {
        var children = new ArrayList<>(component.getChildren().toList());
        // TabSheet content is not attached without a UI, so getChildren() omits it; pull each tab's
        // content component (stored on add) explicitly so the traversal reaches the tab contents.
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
