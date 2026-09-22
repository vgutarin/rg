package vg.rg.frontend.vaadin.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vg.rg.frontend.vaadin.component.button.RGButtonTheme;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.MainView;
import vg.rg.frontend.vaadin.view.auth.AccessDeniedErrorView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.workspace.WorkspaceEventAudienceModel;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.rg.service.resource.ResourcePublicationService;

@PageTitle("page.home.title")
@Route(value = "", layout = MainView.class)
@PermitAll
public class LandingView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final transient AuthenticationContext authenticationContext;
    private final transient ResourcePublicationService publicationService;
    private final transient WorkspaceMembershipRequestService membershipRequestService;
    private final H1 welcome = new H1();
    private final DisclosureList events = new DisclosureList();

    public LandingView(LocalizationService localization,
                       AuthenticationContext authenticationContext,
                       ResourcePublicationService publicationService,
                       WorkspaceMembershipRequestService membershipRequestService) {
        this.localization = localization;
        this.authenticationContext = authenticationContext;
        this.publicationService = publicationService;
        this.membershipRequestService = membershipRequestService;
        addClassName("secure-view");
        add(welcome, events);
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
        events.reset();
        resumeJoinIntent();
        var page = publicationService.browseWorkspaceEvents(PageRequest.of(0, 100,
                Sort.by("startAt").ascending().and(Sort.by("title").ascending()).and(Sort.by("uniqueId").ascending())));
        if (page.isEmpty()) {
            events.add(new Paragraph(localization.i18n("home.events.empty")));
            return;
        }
        page.forEach(this::addEvent);
    }

    private void addEvent(WorkspaceEventAudienceModel event) {
        var body = events.addItem(event.getUniqueId(), event.getTitle(),
                localization.formatEventDateTime(event.getStartAt(),
                        vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions.builder().showShortDayName(true).build()),
                (com.vaadin.flow.component.Component) null);
        body.add(DisclosureList.detailRow(VaadinIcon.FLAG, localization.i18n("event.field.type"),
                localization.i18n("event.type." + event.getEventType().name().toLowerCase()), false));
        if (event.getEndAt() != null) {
            body.add(DisclosureList.detailRow(VaadinIcon.CALENDAR, localization.i18n("event.field.end-at"),
                    localization.formatEventDateTime(event.getEndAt(),
                            vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions.builder().showShortDayName(true).build()), false));
        }
        if (event.getLocation() != null && event.getLocation().getName() != null) {
            body.add(DisclosureList.detailRow(VaadinIcon.MAP_MARKER, localization.i18n("event.field.location"),
                    event.getLocation().getName(), false));
        }
        if (event.getMaxParticipantCount() != null) {
            body.add(DisclosureList.detailRow(VaadinIcon.USER, localization.i18n("event.field.max-participant-count"),
                    event.getMaxParticipantCount().toString(), false));
        }
        if (event.isMembershipPending()) {
            body.add(new Paragraph(localization.i18n("home.events.membership.pending")));
        } else if (!event.isMembershipBound()) {
            var join = new Button(localization.i18n("home.events.membership.join"), ignored -> join(event));
            join.addThemeVariants(ButtonVariant.PRIMARY);
            join.addThemeName(RGButtonTheme.BORDERED);
            body.add(join);
        }
    }

    private void join(WorkspaceEventAudienceModel event) {
        var principal = authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class).orElse(null);
        if (principal == null) {
            return;
        }
        if (principal.userUniqueId() == null) {
            consentForJoin(event);
            return;
        }
        try {
            membershipRequestService.requestJoin(event.getUniqueId());
            Notification.show(localization.i18n("home.events.membership.requested"));
            render();
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
        }
    }

    private void consentForJoin(WorkspaceEventAudienceModel event) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("home.events.membership.consent.title"));
        var consent = new Checkbox(localization.i18n("home.events.membership.consent"));
        var continueButton = new Button(localization.i18n("home.events.membership.continue"), ignored -> {
            JoinSocietyIntent.remember(event.getUniqueId());
            dialog.close();
            getUI().ifPresent(ui -> ui.navigate("login"));
        });
        continueButton.setEnabled(false);
        consent.addValueChangeListener(change -> continueButton.setEnabled(Boolean.TRUE.equals(change.getValue())));
        var cancel = new Button(localization.i18n("events.cancel"), ignored -> dialog.close());
        dialog.add(new Paragraph(localization.i18n("home.events.membership.consent.description")), consent);
        dialog.getFooter().add(cancel, continueButton);
        dialog.open();
    }

    private void resumeJoinIntent() {
        var principal = authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class).orElse(null);
        if (principal == null || principal.userUniqueId() == null || !JoinSocietyIntent.isPending()) {
            return;
        }
        JoinSocietyIntent.consume().ifPresent(eventId -> {
            try {
                membershipRequestService.requestJoin(eventId);
                Notification.show(localization.i18n("home.events.membership.requested"));
            } catch (RuntimeException failure) {
                Notification.show(localization.i18n(failure));
            }
        });
    }

    private boolean hasPrincipal() {
        return authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class)
                .isPresent();
    }

    String welcomeText() {
        return welcome.getText();
    }
}
