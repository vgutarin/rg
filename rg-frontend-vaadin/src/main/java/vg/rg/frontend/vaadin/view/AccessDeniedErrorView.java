package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.MainView;
import vg.rg.frontend.vaadin.service.LocalizationService;

@PageTitle("auth.denied.title")
@Route(value = "access-denied", layout = MainView.class)
@PermitAll
public class AccessDeniedErrorView extends VerticalLayout implements LocaleChangeObserver {
    private final AuthorizationStatusComponent status;

    public AccessDeniedErrorView(LocalizationService localization) {
        this.status = new AuthorizationStatusComponent(localization, () -> { });
        addClassNames("secure-view", "status-view");
        add(status);
        status.show(AuthorizationUiState.DENIED);
    }

    @Override public void localeChange(LocaleChangeEvent event) { status.localeChange(event); }
}
