package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.Locale;
import java.util.Objects;

public final class AuthorizationStatusComponent extends Div implements LocaleChangeObserver {

    private final LocalizationService localization;
    private final Runnable retryAction;
    private final Div protectedContent = new Div();
    private final Div statusCard = new Div();
    private final Icon mark = VaadinIcon.SHIELD.create();
    private final H1 heading = new H1();
    private final Paragraph description = new Paragraph();
    private final ProgressBar progress = new ProgressBar();
    private final Button retry = new Button();
    private AuthorizationUiState state = AuthorizationUiState.LOADING;

    public AuthorizationStatusComponent(LocalizationService localization, Runnable retryAction) {
        this.localization = Objects.requireNonNull(localization);
        this.retryAction = Objects.requireNonNull(retryAction);

        addClassName("authorization-status");
        getElement().setAttribute("role", "status");
        getElement().setAttribute("aria-live", "polite");
        getElement().setAttribute("aria-atomic", "true");

        protectedContent.addClassName("authorization-protected-content");
        statusCard.addClassNames("authorization-status-card", "semantic-card");
        mark.addClassName("authorization-status-mark");
        mark.getElement().setAttribute("aria-hidden", "true");
        heading.addClassName("authorization-status-heading");
        heading.getElement().setAttribute("tabindex", "-1");
        description.addClassName("authorization-status-description");
        progress.addClassName("authorization-status-progress");
        progress.setIndeterminate(true);
        retry.addClassName("authorization-status-action");
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.addClickListener(event -> activateRetry());

        statusCard.add(mark, heading, description, progress, retry);
        add(protectedContent, statusCard);
        show(AuthorizationUiState.LOADING);
    }

    public void show(AuthorizationUiState nextState, Component... permittedContent) {
        state = Objects.requireNonNull(nextState);
        protectedContent.removeAll();
        if (state == AuthorizationUiState.PERMITTED && permittedContent != null) {
            protectedContent.add(permittedContent);
        }
        protectedContent.setVisible(state == AuthorizationUiState.PERMITTED
                && protectedContent.getChildren().findAny().isPresent());
        render(true);
    }

    public AuthorizationUiState state() {
        return state;
    }

    public boolean retryVisible() {
        return retry.isVisible();
    }

    public boolean retryEnabled() {
        return retry.isEnabled();
    }

    public String headingText() {
        return heading.getText();
    }

    public void activateRetry() {
        if (state != AuthorizationUiState.TEMPORARILY_UNAVAILABLE) {
            return;
        }
        show(AuthorizationUiState.RETRYING);
        retryAction.run();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render(false);
    }

    private void render(boolean focusTerminalHeading) {
        getElement().setAttribute("data-authorization-state",
                state.name().toLowerCase(Locale.ROOT));
        getElement().setAttribute("aria-label", localization.i18n("aria.status"));
        heading.setText(localization.i18n(state.messagePrefix() + ".title"));
        heading.getElement().setAttribute("aria-label",
                localization.i18n("aria.authorization-status-heading"));
        description.setText(localization.i18n(state.messagePrefix() + ".description"));
        progress.setVisible(state.showsProgress());
        progress.getElement().setAttribute("aria-label",
                localization.i18n("aria.authorization-progress"));
        retry.setText(localization.i18n("auth.retry"));
        retry.getElement().setAttribute("aria-label",
                localization.i18n("aria.retry-authorization"));
        retry.setVisible(state.showsRetry());
        retry.setEnabled(state == AuthorizationUiState.TEMPORARILY_UNAVAILABLE);
        if (focusTerminalHeading && state.focusesHeading()) {
            heading.getElement().executeJs("this.focus({preventScroll: true})");
        }
    }
}
