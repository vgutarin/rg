package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.MainView;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.Permissions;

@PageTitle("page.reports.title")
@Route(value = "reports", layout = MainView.class)
@PermitAll
public class ReportsView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient AuthenticationContext authenticationContext;
    private final Div protectedContent = new Div();

    public ReportsView(LocalizationService localization,
                       AuthorityChecker authorityChecker,
                       AuthenticationContext authenticationContext) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.authenticationContext = authenticationContext;
        addClassName("secure-view");
        add(protectedContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        protectedContent.removeAll();
        if (!authorityChecker.hasAuthority(Permissions.Reports.VIEW)) {
            event.rerouteTo(hasNoEffectivePermissions() ? NoAccessView.class : AccessDeniedErrorView.class);
            return;
        }
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (authorityChecker.hasAuthority(Permissions.Reports.VIEW)) {
            render();
        } else {
            protectedContent.removeAll();
        }
    }

    String requiredPermission() { return Permissions.Reports.VIEW; }

    private void render() {
        protectedContent.removeAll();
        protectedContent.addClassNames("semantic-card", "aura-surface");
        protectedContent.add(new H1(localization.i18n("reports.title")),
                new Paragraph(localization.i18n("reports.empty")));
    }

    private boolean hasNoEffectivePermissions() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .filter(principal -> principal.userUniqueId() == null
                        || Permissions.recognized(principal.permissions()).isEmpty())
                .isPresent();
    }
}
