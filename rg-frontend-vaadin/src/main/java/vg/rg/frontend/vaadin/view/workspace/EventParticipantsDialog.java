package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import vg.rg.frontend.vaadin.component.button.RGButtonTheme;
import vg.rg.frontend.vaadin.component.scroll.ScrollCue;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.service.workspace.WorkspaceParticipantService;
import vg.rg.service.workspace.event.WorkspaceEventRegistrationService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages one event's participant roster: register the workspace's known people, reorder them, and
 * unregister them.
 *
 * <p>Reached from a "Participants" action on an event row rather than a route — there is no
 * URL-parameter precedent in this module, and every operation is already scoped by the event held in
 * memory. A two-tab sheet separates the ordered registered roster (move-up / move-down / unregister per
 * row) from the add tab, which reuses the participant browse the participants screen uses, showing only
 * people not already registered. The tab caption carries the live registered count.
 *
 * <p>Mobile-first: full width, one column of rows, reorder actions grouped side by side within a row.
 * All text goes through {@link LocalizationService}.
 */
@Slf4j
class EventParticipantsDialog {

    /** How many known participants one page of the add section holds. */
    static final int BROWSE_PAGE_SIZE = 50;

    private final LocalizationService localization;
    private final WorkspaceEventRegistrationService registrationService;
    private final WorkspaceParticipantService participantService;
    private final UniqueId workspaceId;
    private final WorkspaceEventModel event;

    private final Dialog dialog = new Dialog();
    private final TabSheet tabs = new TabSheet();
    private final Tab registeredTab = new Tab();
    /** The scrollable list of registered rows, with its own up/down scroll cues. */
    private final ScrollCue registeredSection = new ScrollCue();
    /** The scrollable list of addable rows, with its own up/down scroll cues. */
    private final ScrollCue addResults = new ScrollCue();
    private final Div addFooter = new Div();
    private final TextField addSearch = new TextField();

    private int loadedPages = 1;

    EventParticipantsDialog(LocalizationService localization,
                            WorkspaceEventRegistrationService registrationService,
                            WorkspaceParticipantService participantService,
                            UniqueId workspaceId, WorkspaceEventModel event) {
        this.localization = localization;
        this.registrationService = registrationService;
        this.participantService = participantService;
        this.workspaceId = workspaceId;
        this.event = event;

        addSearch.setClearButtonVisible(true);
        addSearch.setWidthFull();
        addSearch.setPlaceholder(localization.i18n("events.participants.search.placeholder"));
        addSearch.setValueChangeMode(ValueChangeMode.LAZY);
        addSearch.addValueChangeListener(ignored -> {
            loadedPages = 1;
            renderAddResults();
        });
    }

    Dialog open() {
        dialog.setHeaderTitle(localization.getTranslation("events.participants.title",
                localization.getCurrentLocale(), event.getTitle()));
        dialog.setWidth("100%");

        // A close affordance in the header corner rather than a footer button.
        var close = new Button(VaadinIcon.CLOSE.create(), ignored -> dialog.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        close.setAriaLabel(localization.i18n("events.participants.close"));
        dialog.getHeader().add(close);

        // Both tabs use the same shape: a scroll viewport that shows an up chevron when there is content
        // above and a down chevron when there is content below (see ScrollCue).
        addFooter.addClassName("browse-footer");
        var addSection = new Div(addSearch, addResults, addFooter);
        addSection.addClassName("event-participants__add");

        tabs.setWidthFull();
        tabs.addClassName("event-participants");
        tabs.add(registeredTab, registeredSection);
        tabs.add(new Tab(localization.i18n("events.participants.add")), addSection);
        dialog.add(tabs);

        renderRegistered();
        renderAddResults();
        dialog.open();
        return dialog;
    }

    /** The registered tab's caption, carrying the live count, e.g. "Registered 3". */
    private void updateRegisteredTabLabel(int count) {
        registeredTab.setLabel(localization.getTranslation("events.participants.registered-tab",
                localization.getCurrentLocale(), count));
    }

    // --- Registered section -------------------------------------------------------------------------

    private void renderRegistered() {
        registeredSection.clearContent();
        var roster = registrationService.list(event.getUniqueId());
        updateRegisteredTabLabel(roster.size());
        if (roster.isEmpty()) {
            registeredSection.addContent(new Paragraph(localization.i18n("events.participants.empty")));
            return;
        }
        for (var index = 0; index < roster.size(); index++) {
            registeredSection.addContent(registeredRow(roster.get(index), index == 0, index == roster.size() - 1));
        }
    }

    private Component registeredRow(WorkspaceEventRegistrationModel registration, boolean first, boolean last) {
        var actions = new Div();
        actions.addClassName("event-participants__row-actions");
        var up = new Button(VaadinIcon.ARROW_UP.create(), ignored -> moveUp(registration));
        up.addThemeVariants(ButtonVariant.TERTIARY);
        up.setEnabled(!first);
        up.setAriaLabel(localization.i18n("events.participants.move-up"));
        var down = new Button(VaadinIcon.ARROW_DOWN.create(), ignored -> moveDown(registration));
        down.addThemeVariants(ButtonVariant.TERTIARY);
        down.setEnabled(!last);
        down.setAriaLabel(localization.i18n("events.participants.move-down"));
        var remove = new Button(VaadinIcon.CLOSE.create(), ignored -> unregister(registration));
        remove.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
        remove.setAriaLabel(localization.i18n("events.participants.unregister"));
        actions.add(up, down, remove);
        return participantRow(registration.getParticipantLabel(), actions);
    }

    private void moveUp(WorkspaceEventRegistrationModel registration) {
        act(() -> registrationService.moveUp(registration.getUniqueId()));
        keepRowUnderCursor(-1);
    }

    private void moveDown(WorkspaceEventRegistrationModel registration) {
        act(() -> registrationService.moveDown(registration.getUniqueId()));
        keepRowUnderCursor(1);
    }

    /**
     * After a reorder re-render, shifts the registered list's own scroll by one row in the move
     * direction, keeping the moved row — and the button just tapped — at the same screen position so
     * repeated moves land on the same spot. Delegated to {@link ScrollCue#scrollByRows(int)}.
     */
    private void keepRowUnderCursor(int direction) {
        registeredSection.scrollByRows(direction);
    }

    private void unregister(WorkspaceEventRegistrationModel registration) {
        act(() -> {
            registrationService.unregister(registration.getUniqueId());
            Notification.show(localization.i18n("events.participants.unregistered"));
        });
    }

    // --- Add section --------------------------------------------------------------------------------

    private void renderAddResults() {
        addResults.clearContent();
        addFooter.removeAll();
        var filter = addSearch.getValue() == null ? "" : addSearch.getValue().trim();
        var page = participantService.browse(workspaceId, filter,
                PageRequest.of(0, loadedPages * BROWSE_PAGE_SIZE));
        var registered = registeredParticipantIds();
        var available = page.getContent().stream()
                .filter(participant -> !registered.contains(participant.getUniqueId()))
                .toList();
        if (available.isEmpty()) {
            addResults.addContent(new Paragraph(localization.i18n(filter.isBlank()
                    ? "events.participants.none-available" : "events.participants.search.no-results")));
            return;
        }
        available.forEach(participant -> addResults.addContent(availableRow(participant)));
        renderAddFooter(page.getContent().size(), page.getTotalElements());
    }

    private Component availableRow(WorkspaceParticipantModel participant) {
        // Icon-only "+", on the same line as the name and pushed to the trailing edge by the row's
        // space-between layout. Its accessible name still says what it does. The register handler needs the
        // row so it can animate it out, so the row is built first and the button added afterwards.
        var row = participantRow(participant.getLabel());
        var register = new Button(VaadinIcon.PLUS.create(), ignored -> register(participant, row));
        register.addThemeVariants(ButtonVariant.TERTIARY);
        register.setAriaLabel(localization.i18n("events.participants.register"));
        row.add(register);
        return row;
    }

    /**
     * The shared row skeleton for both tabs: a {@code event-participants__row} holding a
     * {@code event-participants__name} label, with any trailing action components (reorder/remove buttons,
     * or the add "+") appended. The row's {@code space-between} layout pushes those actions to the trailing
     * edge. Returns the {@link Div} so callers that need the row after building it (to append a
     * row-aware button, or to animate it out) can hold on to it.
     */
    private Div participantRow(String label, Component... trailing) {
        var row = new Div();
        row.addClassName("event-participants__row");
        var name = new Span(label);
        name.addClassName("event-participants__name");
        row.add(name);
        row.add(trailing);
        return row;
    }

    private void renderAddFooter(int shown, long total) {
        if (shown >= total) {
            return;
        }
        var count = new Paragraph(localization.getTranslation("events.participants.showing",
                localization.getCurrentLocale(), shown, total));
        count.addClassName("browse-count");
        var more = new Button(localization.i18n("events.participants.load-more"), ignored -> {
            loadedPages++;
            renderAddResults();
        });
        more.addThemeName(RGButtonTheme.BORDERED);
        more.setWidthFull();
        addFooter.add(count, more);
    }

    /**
     * Registers a participant and stays on the Register tab. On success the just-registered row animates
     * out — collapsing and fading — and is then removed, so the person visibly leaves the add list without
     * a full re-render pulling the list out from under the user. The registered tab (roster and its count)
     * is refreshed in the background. On failure nothing is removed and the error is shown.
     */
    private void register(WorkspaceParticipantModel participant, Component row) {
        try {
            registrationService.register(event.getUniqueId(), participant.getUniqueId());
        } catch (IllegalArgumentException invalid) {
            // The business layer returns a stable message key; translation belongs here.
            Notification.show(localization.i18n(invalid.getMessage()));
            return;
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
            return;
        }
        Notification.show(localization.i18n("events.participants.registered-notice"));
        animateRowOut(row);
        // The Register tab stays selected; only the registered roster behind it is refreshed.
        try {
            renderRegistered();
        } catch (RuntimeException refreshFailed) {
            log.debug("Registered roster refresh failed after a successful registration", refreshFailed);
        }
    }

    /**
     * Collapses and fades a row out, then removes it from its parent. The removal is deferred to the
     * client's animation end rather than a server timer, so the element is gone exactly when the
     * transition finishes. If the transition never fires (reduced motion, detached), the fallback timeout
     * still removes it.
     */
    private void animateRowOut(Component row) {
        row.addClassName("event-participants__row--leaving");
        row.getElement().executeJs("""
                const el = this;
                const done = () => el.remove();
                let removed = false;
                const once = () => { if (!removed) { removed = true; done(); } };
                el.addEventListener('transitionend', once, { once: true });
                setTimeout(once, 400);
                """);
    }

    private Set<UniqueId> registeredParticipantIds() {
        return registrationService.list(event.getUniqueId()).stream()
                .map(WorkspaceEventRegistrationModel::getParticipantUniqueId)
                .collect(Collectors.toSet());
    }

    /**
     * Runs a mutation, then re-renders both sections. A refresh failure is cosmetic and must never make a
     * committed change look rejected, so it is logged and swallowed; a failure inside the action itself is
     * reported to the user by the caller and stops the refresh.
     */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
            return;
        }
        try {
            renderRegistered();
            renderAddResults();
        } catch (RuntimeException refreshFailed) {
            log.debug("Event roster refresh failed after a successful change", refreshFailed);
        }
    }

    /** The registered tab, for tests to read its count-bearing caption. */
    Tab registeredTabForTest() {
        return registeredTab;
    }

    /** The registered section, for tests to walk its rows and action buttons. */
    Component registeredSectionForTest() {
        return registeredSection;
    }

    /** The add-results section, for tests to walk its rows and register buttons. */
    Component addResultsForTest() {
        return addResults;
    }

    /** The registered rows' labels, for tests. */
    List<String> registeredLabels() {
        return descendantsOf(registeredSection)
                .filter(child -> child.getElement().getClassList().contains("event-participants__name"))
                .map(child -> child.getElement().getText())
                .toList();
    }

    /** The available (addable) rows' labels, for tests. */
    List<String> availableLabels() {
        return descendantsOf(addResults)
                .filter(child -> child.getElement().getClassList().contains("event-participants__name"))
                .map(child -> child.getElement().getText())
                .toList();
    }

    private static java.util.stream.Stream<Component> descendantsOf(Component component) {
        return component.getChildren().flatMap(child ->
                java.util.stream.Stream.concat(java.util.stream.Stream.of(child), descendantsOf(child)));
    }
}
