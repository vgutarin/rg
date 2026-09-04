package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.ParticipantDescriptor;
import vg.rg.model.ParticipantPhone;
import vg.rg.model.WorkspaceParticipantModel;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.rg.service.DuplicateParticipantPhoneException;
import vg.rg.service.ParticipantLimitReachedException;
import vg.rg.service.WorkspaceParticipantService;
import vg.rg.service.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.List;

/**
 * The people registered in the active workspace who have not come through Telegram, and may never do so.
 *
 * <p><strong>Deliberately the same screen shape as {@link WorkspaceLocationsView}</strong> — two tabs
 * (browse with a filter, add), a single-open accordion of rows, management actions revealed inside the
 * expanded panel. Both accordions are the same {@link DisclosureList} and therefore literally the same
 * styles, so the two screens cannot drift apart by one of them being restyled.
 *
 * <p><strong>A phone number is never rendered until it is asked for.</strong> A row shows only a phone
 * glyph, and only when there is a number — presence, no digits, not even a masked suffix, because a
 * suffix is still information about a person shown to anyone who can see the screen. Revealing makes a
 * separate call against a separate permission and puts the result in a dialog the user dismisses. The
 * roster model it renders from has nowhere to put a number, so this cannot be bypassed by accident.
 *
 * <p>Entry is gated on the app-wide workspace permission, and only that, exactly as
 * {@link WorkspaceLayout} explains: a workspace owner has complete authority over the workspace's
 * contents, so gating on a participant capability could show them less than they can actually use.
 */
@Slf4j
@PageTitle("page.workspace-participants.title")
@Route(value = "workspaces/participants", layout = WorkspaceLayout.class)
@PermitAll
public class WorkspaceParticipantsView extends VerticalLayout
        implements BeforeEnterObserver, LocaleChangeObserver {

    /** How many participants one page of the browse tab holds. */
    static final int BROWSE_PAGE_SIZE = 50;

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient WorkspaceParticipantService participantService;
    private final transient WorkspaceSelectionService selectionService;

    private final Div content = new Div();
    private final DisclosureList browseList = new DisclosureList();
    private final TextField browseSearch = new TextField();
    /** Holds the count line and the Load-more button, below the list. */
    private final Div browseFooter = new Div();

    /**
     * How many pages the browse tab has loaded. Pages accumulate rather than replace, so "Load more"
     * appends; anything that changes the result set — a filter edit, a write — resets this to one.
     */
    private int loadedPages = 1;

    /** Preserved across re-renders (e.g. a language switch) so the selected tab stays selected. */
    private int selectedTabIndex;

    /** The workspace every query on this screen is scoped to, resolved once per navigation. */
    private UniqueId workspaceId;

    public WorkspaceParticipantsView(LocalizationService localization,
                                     AuthorityChecker authorityChecker,
                                     WorkspaceParticipantService participantService,
                                     WorkspaceSelectionService selectionService) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.participantService = participantService;
        this.selectionService = selectionService;

        browseSearch.setClearButtonVisible(true);
        browseSearch.setWidthFull();
        browseSearch.setValueChangeMode(ValueChangeMode.LAZY);
        browseSearch.addValueChangeListener(event -> {
            // A new filter is a new result set, so accumulated pages no longer mean anything.
            loadedPages = 1;
            renderBrowseList(event.getValue());
        });

        addClassName("secure-view");
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // The layout already gates the section; this keeps the view safe if it is ever routed directly.
        if (!authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            event.rerouteTo(NoAccessView.class);
            return;
        }
        // Resolved here rather than read from the surrounding layout: the selection service repairs a
        // stale pointer, so this screen cannot open against a workspace that no longer exists.
        var active = selectionService.activeWorkspace();
        workspaceId = active == null ? null : active.getUniqueId();
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (workspaceId != null && authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            render();
        } else {
            content.removeAll();
        }
    }

    private void render() {
        content.removeAll();
        if (workspaceId == null) {
            return;
        }
        content.setWidthFull();
        content.addClassNames("semantic-card", "aura-surface");
        content.add(new H1(localization.i18n("page.workspace-participants.title")));

        var tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(localization.i18n("participants.tab.browse"), browseTab());
        tabs.add(localization.i18n("participants.tab.add"), addTab());
        tabs.setSelectedIndex(Math.min(selectedTabIndex, 1));
        tabs.addSelectedChangeListener(event -> selectedTabIndex = tabs.getSelectedIndex());
        content.add(tabs);

        renderBrowseList(browseSearch.getValue());
    }

    // --- Browse tab ---------------------------------------------------------------------------------

    private Component browseTab() {
        browseSearch.setPlaceholder(localization.i18n("participants.search.placeholder"));
        browseFooter.addClassName("browse-footer");
        var layout = new VerticalLayout(browseSearch, browseList, browseFooter);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        return layout;
    }

    /**
     * Renders the pages loaded so far, ordered by label, optionally filtered.
     *
     * <p>One service call covers both the filtered and unfiltered cases, so the two cannot disagree
     * about order. Pages <em>accumulate</em>: this always asks for everything loaded so far in a single
     * request rather than fetching page N alone, because ordering by label costs a full roster scan
     * whichever page is wanted — so one call for {@code n} pages is no more expensive than one call for
     * the last of them, and it keeps the rendered list a single consistent snapshot.
     */
    private void renderBrowseList(String query) {
        browseList.reset();
        browseFooter.removeAll();
        var trimmed = query == null ? "" : query.trim();

        var page = participantService.browse(
                workspaceId, trimmed, PageRequest.of(0, loadedPages * BROWSE_PAGE_SIZE));
        var shown = page.getContent();
        if (shown.isEmpty()) {
            browseList.add(new Paragraph(localization.i18n(
                    trimmed.isBlank() ? "participants.empty" : "participants.search.no-results")));
            return;
        }
        shown.forEach(this::addItem);
        renderBrowseFooter(shown.size(), page.getTotalElements());
    }

    /**
     * The count line and, when there is more, the Load-more button.
     *
     * <p>Stating the total is the point: an alphabetical list that silently stops part-way looks like a
     * list of everyone, so a participant whose name sorts late would appear not to exist at all. The
     * count is free — the service already knows it from the scan the ordering required.
     */
    private void renderBrowseFooter(int shown, long total) {
        if (shown >= total) {
            return;
        }
        var count = new Paragraph(localization.getTranslation(
                "participants.showing", localization.getCurrentLocale(), shown, total));
        count.addClassName("browse-count");
        var more = new Button(localization.i18n("participants.load-more"), event -> {
            loadedPages++;
            renderBrowseList(browseSearch.getValue());
        });
        more.setWidthFull();
        browseFooter.add(count, more);
    }

    /**
     * Appends one participant to the accordion. The header shape, the chevron and the single-open
     * behaviour come from {@link DisclosureList}; this decides only the marker and the panel.
     *
     * <p>The participant's identifier is the entry's key, which is what keeps an expanded row expanded
     * across the re-render that follows an edit — so the user sees their change in the panel they were
     * already reading, including when the new label moves the row elsewhere in the alphabet.
     */
    private void addItem(WorkspaceParticipantModel participant) {
        fillDetailPanel(
                browseList.addItem(participant.getUniqueId(), participant.getLabel(), null,
                        phoneMarker(participant)),
                participant);
    }

    /**
     * A phone glyph, or nothing at all when no number is recorded.
     *
     * <p>Presence is the whole message — no digits and no masked suffix, since a suffix is still
     * information about a person disclosed to whoever can see the screen. It carries an accessible name
     * because a glyph whose presence is the signal is invisible to a screen reader otherwise.
     */
    private Component phoneMarker(WorkspaceParticipantModel participant) {
        if (!participant.isPhoneRecorded()) {
            return null;
        }
        var glyph = VaadinIcon.PHONE.create();
        var label = localization.i18n("participant.phone.present");
        glyph.getElement().setAttribute("role", "img");
        glyph.getElement().setAttribute("aria-label", label);
        glyph.getElement().setAttribute("title", label);
        return glyph;
    }

    /**
     * The expanded panel: a primary reveal action when there is a number to reveal, then edit and
     * remove. Management actions live in here rather than in the row, so they are shown on intent —
     * the arrangement the locations screen uses.
     */
    private void fillDetailPanel(Div body, WorkspaceParticipantModel participant) {
        if (participant.isPhoneRecorded()
                && authorityChecker.hasAuthority(participant.getUniqueId(),
                        LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT)) {
            var reveal = new Button(localization.i18n("participants.reveal"), VaadinIcon.EYE.create(),
                    event -> revealContact(participant));
            reveal.addThemeVariants(ButtonVariant.PRIMARY);
            reveal.setWidthFull();
            body.add(reveal);
        }

        var actions = new Div();
        actions.addClassName(DisclosureList.ROW_ACTIONS);
        if (authorityChecker.hasAuthority(participant.getUniqueId(),
                LocalPermissions.WorkspaceParticipant.UPDATE)) {
            var edit = new Button(localization.i18n("participants.edit"), VaadinIcon.EDIT.create(),
                    event -> openEditDialog(participant));
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            actions.add(edit);
        }
        if (authorityChecker.hasAuthority(participant.getUniqueId(),
                LocalPermissions.WorkspaceParticipant.DELETE)) {
            var remove = new Button(localization.i18n("participants.remove"), VaadinIcon.TRASH.create(),
                    event -> confirmRemove(participant));
            remove.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            actions.add(remove);
        }
        if (actions.getElement().getChildCount() > 0) {
            body.add(actions);
        }
    }

    // --- Add tab ------------------------------------------------------------------------------------

    private Component addTab() {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();

        if (!authorityChecker.hasAuthority(
                workspaceId, LocalPermissions.WorkspaceParticipant.CREATE)) {
            layout.add(new Paragraph(localization.i18n("participants.add.no-permission")));
            return layout;
        }

        var label = labelField();
        var phone = phoneField();
        var save = new Button(localization.i18n("participants.register"), event -> {
            if (register(label.getValue(), phone.getValue())) {
                label.clear();
                phone.clear();
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setWidthFull();

        var form = new Div(label, phone, save);
        form.addClassName("stacked-form");
        form.setWidthFull();
        layout.add(new Paragraph(localization.i18n("participants.add.hint")), form);
        return layout;
    }

    private boolean register(String label, String phone) {
        try {
            participantService.register(workspaceId, ParticipantDescriptor.of(label, phone));
        } catch (ParticipantLimitReachedException limitReached) {
            Notification.show(localization.i18n(ParticipantLimitReachedException.MESSAGE_KEY));
            return false;
        } catch (DuplicateParticipantPhoneException duplicate) {
            Notification.show(localization.i18n(DuplicateParticipantPhoneException.MESSAGE_KEY));
            return false;
        } catch (IllegalArgumentException invalid) {
            // The business layer returns a stable message key; translation belongs here.
            Notification.show(localization.i18n(invalid.getMessage()));
            return false;
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
            return false;
        }
        Notification.show(localization.i18n("participants.registered"));
        afterChange();
        return true;
    }

    // --- Disclosure, editing, removal ---------------------------------------------------------------

    /**
     * Discloses one participant's number, in a dialog the user dismisses themselves.
     *
     * <p>A dialog rather than a notification, deliberately: a notification auto-dismisses on a timer,
     * cannot be closed on purpose, and is the component most likely to be reused somewhere that logs its
     * text. Nothing here is logged, and the plaintext exists only for as long as this dialog does.
     */
    Prompt revealContact(WorkspaceParticipantModel participant) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("participants.reveal.title"));

        var close = new Button(localization.i18n("participants.close"), event -> dialog.close());
        try {
            var revealed = participantService.revealContact(participant.getUniqueId());
            var number = new Span(revealed.phone());
            number.addClassName("participant-revealed-phone");
            dialog.add(number);
        } catch (RuntimeException failure) {
            // Never the exception's own text: it may have come from a layer that saw the value.
            dialog.add(new Paragraph(localization.i18n(failure)));
        }
        dialog.getFooter().add(close);
        dialog.open();
        return new Prompt(dialog, close, close);
    }

    /**
     * Edits a participant's contact data.
     *
     * <p>The phone field opens <strong>empty</strong>, not pre-filled with the current number, and its
     * helper text says so. Pre-filling would disclose the number through a path that carries no reveal
     * permission and leaves no distinction between looking and editing — which would make the whole
     * arrangement decorative.
     */
    Prompt openEditDialog(WorkspaceParticipantModel participant) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("participants.edit.title"));

        var label = labelField();
        label.setValue(participant.getLabel() == null ? "" : participant.getLabel());

        var phone = phoneField();
        phone.setHelperText(localization.i18n("participant.field.phone.edit-helper"));

        var save = new Button(localization.i18n("participants.save"), event -> {
            if (update(participant, label.getValue(), phone.getValue())) {
                dialog.close();
            }
        });
        var cancel = new Button(localization.i18n("participants.cancel"), event -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.add(label, phone);
        dialog.open();
        return new Prompt(dialog, save, cancel);
    }

    private boolean update(WorkspaceParticipantModel participant, String label, String phone) {
        try {
            participantService.update(participant.getUniqueId(), participant.getVersion(),
                    ParticipantDescriptor.of(label, phone));
        } catch (ObjectOptimisticLockingFailureException stale) {
            // Someone else advanced the record; never silently overwrite their change. The dialog stays
            // open with the typed text still in it, so the user retries rather than retypes.
            Notification.show(localization.i18n("participants.stale"));
            return false;
        } catch (DuplicateParticipantPhoneException duplicate) {
            Notification.show(localization.i18n(DuplicateParticipantPhoneException.MESSAGE_KEY));
            return false;
        } catch (IllegalArgumentException invalid) {
            Notification.show(localization.i18n(invalid.getMessage()));
            return false;
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
            return false;
        }
        // Only after the write succeeded, and outside the catch: a failed re-render is cosmetic and must
        // never make a committed change look rejected.
        afterChange();
        return true;
    }

    /** Removal, confirmed first; see {@link Dialogs#confirmDeletion} for its guarantee. */
    Prompt confirmRemove(WorkspaceParticipantModel participant) {
        return Dialogs.confirmDeletion(localization,
                "participants.remove", "participants.remove.confirm", "participants.remove",
                "participants.cancel", () -> remove(participant));
    }

    private void remove(WorkspaceParticipantModel participant) {
        try {
            participantService.delete(participant.getUniqueId());
            Notification.show(localization.i18n("participants.removed"));
            afterChange();
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
        }
    }

    // --- Fields and refresh -------------------------------------------------------------------------

    private TextField labelField() {
        var field = new TextField(localization.i18n("participant.field.label"));
        field.setWidthFull();
        field.setRequiredIndicatorVisible(true);
        // The owner's own name for this person, stored as given. The guidance states the narrow purpose
        // rather than discouraging personal data outright -- here some of it is the point.
        field.setHelperText(localization.i18n("participant.field.label.helper"));
        return field;
    }

    private TextField phoneField() {
        var field = new TextField(localization.i18n("participant.field.phone"));
        field.setWidthFull();
        field.setMaxLength(ParticipantPhone.MAX_INPUT_LENGTH);
        field.setHelperText(localization.i18n("participant.field.phone.helper"));
        return field;
    }

    /**
     * Re-renders the browse list in place, keeping the active filter. Deliberately cannot fail the
     * caller: the mutation has already committed by this point, so a refresh problem is reported and
     * swallowed rather than reversing the outcome.
     */
    private void afterChange() {
        try {
            renderBrowseList(browseSearch.getValue());
        } catch (RuntimeException refreshFailed) {
            log.debug("Participant roster refresh failed after a successful change", refreshFailed);
        }
    }

    /**
     * The labels as currently rendered, for tests. There is deliberately no phone number to expose.
     *
     * <p>Walks the tree rather than stepping a fixed number of levels: the row's DOM shape belongs to
     * {@link DisclosureList}, so a depth hard-coded here would break the moment that changed.
     */
    List<String> renderedLabels() {
        return descendantsOf(browseList)
                .filter(child -> child.getElement().getClassList().contains(DisclosureList.ROW_NAME))
                .map(child -> child.getElement().getText())
                .toList();
    }

    private static java.util.stream.Stream<Component> descendantsOf(Component component) {
        return component.getChildren().flatMap(child ->
                java.util.stream.Stream.concat(java.util.stream.Stream.of(child), descendantsOf(child)));
    }

    UniqueId activeWorkspaceId() {
        return workspaceId;
    }
}
