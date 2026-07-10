package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.MainView;
import vg.rg.frontend.vaadin.service.LocalizationService;

@PageTitle("auth.no-access.title")
@Route(value = "no-access", layout = MainView.class)
@PermitAll
public class NoAccessView extends VerticalLayout implements LocaleChangeObserver {
    private final AuthorizationStatusComponent status;

    public NoAccessView(LocalizationService localization) {
        this.status = new AuthorizationStatusComponent(localization, () -> { });
        addClassNames("secure-view", "status-view");
        add(status);
        status.show(AuthorizationUiState.NO_ACCESS);
    }

    @Override public void localeChange(LocaleChangeEvent event) { status.localeChange(event); }
}
