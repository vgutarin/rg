package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
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
import vg.rg.service.ProtectedActionService;

import java.util.UUID;

@PageTitle("page.home.title")
@Route(value = "", layout = MainView.class)
@PermitAll
public class LandingView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient AuthenticationContext authenticationContext;
    private final ProtectedActionService protectedActionService;
    private final Div protectedContent = new Div();

    public LandingView(LocalizationService localization,
                       AuthorityChecker authorityChecker,
                       AuthenticationContext authenticationContext,
                       ProtectedActionService protectedActionService) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.authenticationContext = authenticationContext;
        this.protectedActionService = protectedActionService;
        addClassName("secure-view");
        protectedContent.addClassName("content-grid");
        add(protectedContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        protectedContent.removeAll();
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
        protectedContent.removeAll();
        var hero = card("hero-card");
        hero.add(new H1(localization.i18n("home.title")),
                new Paragraph(localization.i18n("home.description")));
        var access = card("access-card");
        access.add(new H2(localization.i18n("home.access.title")));
        var principal = authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .orElseThrow(() -> new IllegalStateException("No authenticated opaque principal"));
        var effectivePermissions = principal.userUniqueId() == null
                ? java.util.Set.<String>of()
                : Permissions.recognized(principal.permissions());
        effectivePermissions.forEach(permission ->
                access.add(new Paragraph(localization.i18n("permission." + permission))));
        if (authorityChecker.hasAuthority(Permissions.Request.SUBMIT)) {
            var result = new Paragraph();
            result.getElement().setAttribute("aria-live", "polite");
            var submit = new Button(localization.i18n("request.submit"));
            submit.addClassName("primary-action");
            submit.addThemeVariants(ButtonVariant.PRIMARY);
            var idempotencyKey = UUID.randomUUID();
            submit.addClickListener(event -> {
                submit.setEnabled(false);
                var outcome = protectedActionService.submit(idempotencyKey);
                result.setText(localization.i18n(outcome.messageKey()));
                submit.setEnabled(true);
            });
            access.add(submit, result);
        }
        protectedContent.add(hero, access);
    }

    private boolean hasPrincipal() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .isPresent();
    }

    private Div card(String className) {
        var card = new Div();
        card.addClassNames("semantic-card", "aura-surface", className);
        return card;
    }
}
