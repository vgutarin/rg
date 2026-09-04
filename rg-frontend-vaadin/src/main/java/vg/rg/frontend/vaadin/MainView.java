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
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.rg.service.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@PermitAll
public class MainView extends AppLayout implements AfterNavigationObserver, LocaleChangeObserver {

    private final transient AuthenticationContext authenticationContext;
    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient WorkspaceSelectionService selectionService;
    private final boolean authenticated;
    private final boolean telegramFlow;
    private final Set<String> permissions;
    private final H2 viewTitle = new H2();
    private final H1 appTitle = new H1();
    private final DrawerToggle drawerToggle = new DrawerToggle();
    private final Select<Locale> localePicker = new Select<>();
    private final Button sessionAction = new Button();
    private final List<NavBinding> navigation = new ArrayList<>();

    /**
     * Message key for the current view's title, taken from the leaf route target on each navigation.
     *
     * <p>Remembered rather than re-derived, because a locale change has to retranslate it and carries no
     * navigation event to ask.
     */
    private String currentTitleKey;

    private record NavBinding(SideNavItem item, String key) { }

    public MainView(LocalizationService localization,
                    AuthenticationContext authenticationContext,
                    AuthorityChecker authorityChecker,
                    WorkspaceSelectionService selectionService) {
        this.localization = localization;
        this.authenticationContext = authenticationContext;
        this.authorityChecker = authorityChecker;
        this.selectionService = selectionService;
        var principal = authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class);
        this.authenticated = principal.isPresent();
        this.telegramFlow = principal
                .map(AuthenticatedUserPrincipal::authenticationFlow)
                .filter(AuthenticationFlow.TELEGRAM::equals)
                .isPresent();
        this.permissions = principal
                .filter(current -> current.userUniqueId() != null)
                .map(AuthenticatedUserPrincipal::permissions)
                .map(Permissions::recognized)
                .orElse(Set.of());

        setPrimarySection(Section.DRAWER);
        addClassName("secure-shell");
        // NOT addToNavbar(true, ...): the boolean selects the *touch-optimized* navbar slot, which
        // Vaadin renders at the BOTTOM of the screen on small touchscreens. In a Telegram webview that
        // put the whole header, drawer toggle included, at the foot of the page.
        addToNavbar(header());
        addToDrawer(drawer());
        renderTranslations();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        currentTitleKey = titleKeyOf(event);
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

    /** Where each entry actually goes — a label can move without the route moving with it. */
    List<String> navigationPaths() {
        return navigation.stream().map(binding -> binding.item().getPath()).toList();
    }

    Set<String> visiblePermissions() {
        return permissions;
    }

    String sessionActionText() {
        return sessionAction.getText();
    }

    boolean sessionActionVisible() {
        return sessionAction.isVisible();
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
        // A Telegram Mini App session cannot be re-established from a local login, so logging out would
        // be a dead end; hide the action for the Telegram flow. Unauthenticated users keep "Login", and
        // any future (non-Telegram) flow keeps "Logout".
        sessionAction.setVisible(!telegramFlow);

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

        addNav(nav, "nav.home", "/", VaadinIcon.HOME.create());

        if (permissions.contains(Permissions.Reports.READ)) {
            addNav(nav, "nav.reports", "/reports", VaadinIcon.CHART.create());
        }
        // Locations and participants sit at the top level even though both live inside a workspace: the
        // workspace is the scope, not a place the user has to navigate through.
        //
        // Resolved once and passed in, because resolving the active workspace provisions a default on
        // first entry -- a side effect that should happen once per page load, not once per nav entry.
        addWorkspaceScopedNav(nav, activeWorkspaceId());

        // The workspace section's own entry is deliberately not rendered yet. Its routes, layout and gate
        // all still work -- only the way in from the drawer is withheld, so restoring it means adding one
        // addNav call back here, together with a `nav.workspaces` label.

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

    /**
     * The workspace-scoped entries, each shown only when the caller may actually list that type in the
     * workspace they are working in — a question about the workspace, not about a capability the
     * principal carries around.
     *
     * <p>The app-wide gate is checked before this is reached, and not as a shortcut: resolving the active
     * workspace is itself guarded by it, so asking a non-holder would raise an access denial and take the
     * whole navigation shell down with it. A null workspace means the caller does not hold it.
     */
    private void addWorkspaceScopedNav(SideNav nav, UniqueId workspaceId) {
        if (workspaceId == null) {
            return;
        }
        if (authorityChecker.hasAuthority(workspaceId, LocalPermissions.Location.LIST)) {
            addNav(nav, "nav.locations", "/workspaces/locations", VaadinIcon.MAP_MARKER.create());
        }
        if (authorityChecker.hasAuthority(
                workspaceId, LocalPermissions.WorkspaceParticipant.LIST)) {
            addNav(nav, "nav.participants", "/workspaces/participants", VaadinIcon.USERS.create());
        }
    }

    /**
     * The workspace the caller is working in, or null when they hold no workspace permission.
     *
     * <p>Note this provisions the caller's default workspace on their first page load rather than on
     * their first visit to a workspace screen — resolving the active workspace is what creates one. That
     * is the same guarantee stated earlier, reached earlier; a user without the permission still gets
     * nothing.
     */
    private UniqueId activeWorkspaceId() {
        if (!permissions.contains(Permissions.Workspace.OWNER)) {
            return null;
        }
        var active = selectionService.activeWorkspace();
        return active == null ? null : active.getUniqueId();
    }

    private void addNav(SideNav nav, String key, String path, Component icon) {
        var item = new SideNavItem(localization.i18n(key), path, icon);
        item.addClassName("navigation-item");
        nav.addItem(item);
        navigation.add(new NavBinding(item, key));
    }

    private void configureLocalePicker() {
        localePicker.addClassName("locale-picker");
        // No visible label: the header is one line, and the selected language names itself. The
        // accessible name still has to exist, so it moves to aria-label rather than disappearing.
        localePicker.getElement().setAttribute("aria-label", localization.i18n("locale.label"));
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
        localePicker.getElement().setAttribute("aria-label", localization.i18n("locale.label"));
        localePicker.setItemLabelGenerator(this::localeLabel);
        sessionAction.setText(localization.i18n(authenticated ? "action.logout" : "Login"));
        navigation.forEach(binding -> binding.item().setLabel(localization.i18n(binding.key())));
    }

    /**
     * The {@code @PageTitle} of the <strong>leaf</strong> route target.
     *
     * <p>{@code getContent()} is not the view for a nested route: everything under
     * {@code WorkspaceLayout} makes <em>that layout</em> this layout's content, and it declares no
     * {@code @PageTitle} — which is why the locations and participants screens showed the project name
     * instead of their own. The active chain contains the whole nesting, so this takes the first element
     * that actually declares a title, which works whichever end of the chain the leaf sits at.
     *
     * @return the title's message key, or {@code null} when nothing in the chain declares one
     */
    private static String titleKeyOf(AfterNavigationEvent event) {
        return event.getActiveChain().stream()
                .map(target -> target.getClass().getAnnotation(PageTitle.class))
                .filter(Objects::nonNull)
                .map(PageTitle::value)
                .findFirst()
                .orElse(null);
    }

    private void updateCurrentTitle() {
        var translatedTitle = currentTitleKey == null
                ? localization.i18n("project.name")
                : localization.i18n(currentTitleKey);
        viewTitle.setText(translatedTitle);
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().setTitle(translatedTitle);
        }
    }

    /** The title as currently rendered, for tests. */
    String currentTitleText() {
        return viewTitle.getText();
    }
}
