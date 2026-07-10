package vg.rg.frontend.vaadin.telegram;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.frontend.vaadin.security.ApplicationSecurityContextService;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.LandingView;
import vg.rg.frontend.vaadin.view.NoAccessView;
import vg.rg.frontend.vaadin.view.AuthorizationStatusComponent;
import vg.rg.frontend.vaadin.view.AuthorizationUiState;
import vg.rg.security.AuthorizationApplicationService;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;


@JavaScript("https://telegram.org/js/telegram-web-app.js?63")
@PageTitle("page.login.title")
@Route("login")
@AnonymousAllowed
public class TelegramAuthView extends VerticalLayout implements LocaleChangeObserver {

    private final AuthorizationApplicationService authorizationService;
    private final ApplicationSecurityContextService securityContextService;
    private final LocalizationService localization;
    private final AuthorizationStatusComponent status;
    private boolean requestInFlight;
    private boolean callbackRedeemed;

    public TelegramAuthView(AuthorizationApplicationService authorizationService,
                            ApplicationSecurityContextService securityContextService,
                            LocalizationService localization) {
        this.authorizationService = authorizationService;
        this.securityContextService = securityContextService;
        this.localization = localization;
        this.status = new AuthorizationStatusComponent(localization, this::retryAuthorization);

        addClassName("telegram-auth-view");
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        add(status);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (isAuthenticated()) {
            navigateHome();
        } else {
            requestTelegramInitData();
        }
    }

    @ClientCallable
    public void authenticate(String initData) {
        if (callbackRedeemed) {
            return;
        }
        callbackRedeemed = true;
        requestInFlight = false;
        final TelegramInitDataRequest request;
        try {
            request = new TelegramInitDataRequest(initData);
        } catch (IllegalArgumentException exception) {
            securityContextService.clear();
            show(AuthorizationUiState.DENIED);
            return;
        }

        var outcome = authorizationService.redeem(request);
        if (outcome.status() == AuthorizationOutcome.Status.AUTHORIZED) {
            var principal = outcome.principal().orElseThrow();
            securityContextService.authenticate(principal);
            navigateAuthorized(principal.sub() == null);
        } else if (outcome.status() == AuthorizationOutcome.Status.UNAVAILABLE) {
            securityContextService.clear();
            show(AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
        } else if (outcome.status() == AuthorizationOutcome.Status.INCOMPATIBLE) {
            securityContextService.clear();
            show(AuthorizationUiState.INCOMPATIBLE);
        } else {
            securityContextService.clear();
            show(AuthorizationUiState.DENIED);
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        status.localeChange(event);
        updatePageTitle();
    }

    void requestTelegramInitData() {
        startTelegramAuthorization(false);
    }

    private void retryAuthorization() {
        startTelegramAuthorization(true);
    }

    private void startTelegramAuthorization(boolean retrying) {
        if (requestInFlight) {
            return;
        }
        requestInFlight = true;
        callbackRedeemed = false;
        show(retrying ? AuthorizationUiState.RETRYING : AuthorizationUiState.LOADING);
        getElement().executeJs("""
                const webApp = window.Telegram && window.Telegram.WebApp;
                const initData = webApp && typeof webApp.initData === 'string' ? webApp.initData : '';
                $0.$server.authenticate(initData);
                """, getElement());
    }

    String headingText() {
        return status.headingText();
    }

    boolean retryVisible() {
        return status.retryVisible();
    }

    boolean retryEnabled() {
        return status.retryEnabled();
    }

    void activateRetry() {
        status.activateRetry();
    }

    public AuthorizationUiState authorizationState() {
        return status.state();
    }

    private void show(AuthorizationUiState nextState) {
        status.show(nextState);
        updatePageTitle();
    }

    private void updatePageTitle() {
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().setTitle(localization.i18n("page.login.title"));
        }
    }

    private boolean isAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void navigateHome() {
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate(LandingView.class);
        }
    }

    private void navigateAuthorized(boolean provisional) {
        var ui = UI.getCurrent();
        if (ui != null) {
            if (provisional) {
                ui.navigate(NoAccessView.class);
            } else {
                ui.navigate(LandingView.class);
            }
        }
    }
}
