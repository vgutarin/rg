package vg.rg.frontend.vaadin.view.home;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.MainView;
import vg.rg.frontend.vaadin.view.auth.AccessDeniedErrorView;
import vg.rg.model.security.AuthenticatedUserPrincipal;

@PageTitle("page.home.title")
@Route(value = "", layout = MainView.class)
@PermitAll
public class LandingView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final transient AuthenticationContext authenticationContext;
    private final H1 welcome = new H1();

    public LandingView(LocalizationService localization,
                       AuthenticationContext authenticationContext) {
        this.localization = localization;
        this.authenticationContext = authenticationContext;
        addClassName("secure-view");
        add(welcome);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!hasPrincipal()) {
            event.rerouteTo(AccessDeniedErrorView.class);
            return;
        }
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        welcome.setText(localization.i18n("home.welcome"));
    }

    private boolean hasPrincipal() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .isPresent();
    }

    String welcomeText() {
        return welcome.getText();
    }
}
