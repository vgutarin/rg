package vg.rg.frontend.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.icon.VaadinIcon;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.telegram.TelegramAuthView;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@PermitAll
public class MainView extends AppLayout implements AfterNavigationObserver, LocaleChangeObserver {

    private final transient AuthenticationContext authenticationContext;
    private final LocalizationService localization;
    private final boolean authenticated;
    private final Set<String> permissions;
    private final H2 viewTitle = new H2();
    private final H1 appTitle = new H1();
    private final DrawerToggle drawerToggle = new DrawerToggle();
    private final Select<Locale> localePicker = new Select<>();
    private final Button sessionAction = new Button();
    private final List<NavBinding> navigation = new ArrayList<>();

    private record NavBinding(SideNavItem item, String key) { }

    public MainView(LocalizationService localization, AuthenticationContext authenticationContext) {
        this.localization = localization;
        this.authenticationContext = authenticationContext;
        var principal = authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class);
        this.authenticated = principal.isPresent();
        this.permissions = principal
                .filter(current -> current.sub() != null)
                .map(AuthenticatedUserPrincipal::permissions)
                .map(Permissions::recognized)
                .orElse(Set.of());

        setPrimarySection(Section.DRAWER);
        addClassName("secure-shell");
        addToNavbar(true, header());
        addToDrawer(drawer());
        renderTranslations();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        updateCurrentTitle();
        getElement().executeJs("if (this.hasAttribute('overlay')) this.drawerOpened = false");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        renderTranslations();
        updateCurrentTitle();
    }

    List<String> navigationLabels() {
        return navigation.stream().map(binding -> binding.item().getLabel()).toList();
    }

    Set<String> visiblePermissions() {
        return permissions;
    }

    String sessionActionText() {
        return sessionAction.getText();
    }

    private Component header() {
        viewTitle.addClassName("view-title");

        configureLocalePicker();
        sessionAction.addClickListener(event -> {
            if (authenticated) {
                authenticationContext.logout();
            } else {
                UI.getCurrent().navigate(TelegramAuthView.class);
            }
        });
        sessionAction.addClassName("session-action");

        var actions = new HorizontalLayout(localePicker, sessionAction);
        actions.addClassName("header-actions");
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(false);
        var header = new HorizontalLayout(drawerToggle, viewTitle, actions);
        header.addClassName("main-header");
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(false);
        header.expand(viewTitle);
        return header;
    }

    private Component drawer() {
        appTitle.addClassName("app-title");
        var nav = new SideNav();
        nav.setLabel(localization.i18n("nav.label"));
        if (permissions.contains(Permissions.Home.VIEW)) {
            addNav(nav, "nav.home", "/", VaadinIcon.HOME.create());
        }
        if (permissions.contains(Permissions.Reports.VIEW)) {
            addNav(nav, "nav.reports", "/reports", VaadinIcon.CHART.create());
        }
        var scroller = new Scroller(nav);
        scroller.setSizeFull();
        var drawer = new VerticalLayout(appTitle, scroller);
        drawer.addClassName("drawer-content");
        drawer.setPadding(false);
        drawer.setSpacing(false);
        drawer.setSizeFull();
        drawer.expand(scroller);
        return drawer;
    }

    private void addNav(SideNav nav, String key, String path, Component icon) {
        var item = new SideNavItem(localization.i18n(key), path, icon);
        item.addClassName("navigation-item");
        nav.addItem(item);
        navigation.add(new NavBinding(item, key));
    }

    private void configureLocalePicker() {
        localePicker.addClassName("locale-picker");
        localePicker.setItemLabelGenerator(this::localeLabel);
        localePicker.setItems(localization.getProvidedLocales());
        localePicker.setValue(localization.getCurrentLocale());
        localePicker.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                selectLocale(event.getValue());
            }
        });
    }

    void selectLocale(Locale locale) {
        localization.setCurrentLocale(locale);
    }

    String localeLabel(Locale locale) {
        return localization.i18n("locale." + locale.toLanguageTag());
    }

    Locale selectedLocale() {
        return localePicker.getValue();
    }

    private void renderTranslations() {
        appTitle.setText(localization.i18n("project.name"));
        drawerToggle.getElement().setAttribute("aria-label", localization.i18n("aria.open-navigation"));
        localePicker.setLabel(localization.i18n("locale.label"));
        localePicker.setItemLabelGenerator(this::localeLabel);
        sessionAction.setText(localization.i18n(authenticated ? "action.logout" : "Login"));
        navigation.forEach(binding -> binding.item().setLabel(localization.i18n(binding.key())));
    }

    private void updateCurrentTitle() {
        if (getContent() == null) {
            return;
        }
        var pageTitle = getContent().getClass().getAnnotation(PageTitle.class);
        var translatedTitle = pageTitle == null ? localization.i18n("project.name")
                : localization.i18n(pageTitle.value());
        viewTitle.setText(translatedTitle);
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().setTitle(translatedTitle);
        }
    }
}
