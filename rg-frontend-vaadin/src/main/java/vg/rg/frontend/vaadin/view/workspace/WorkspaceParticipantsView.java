package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.clipboard.Clipboard;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;
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
import vg.rg.config.WorkspaceProperties;
import vg.rg.exception.workspace.DuplicateParticipantPhoneException;
import vg.rg.exception.workspace.ParticipantLimitReachedException;
import vg.rg.frontend.vaadin.component.button.RGButtonTheme;
import vg.rg.frontend.vaadin.component.dialog.Dialogs;
import vg.rg.frontend.vaadin.component.dialog.Prompt;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.ParticipantDescriptor;
import vg.rg.model.workspace.ParticipantPhone;
import vg.rg.model.workspace.WorkspaceMembershipApprovalTarget;
import vg.rg.model.workspace.WorkspaceMembershipRequestModel;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.rg.service.workspace.WorkspaceParticipantService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.stream.Stream;

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
    private final transient WorkspaceMembershipRequestService membershipRequestService;
    private final transient WorkspaceSelectionService selectionService;
    private final WorkspaceProperties workspaceProperties;

    private final Div content = new Div();
    private final DisclosureList browseList = new DisclosureList();
    private final TextField browseSearch = new TextField();
    /** Holds the count line and the Load-more button, below the list. */
    private final Div browseFooter = new Div();

    /** Present only while the active user may create participants. */
    private TabSheet tabs;

    /** Avoids rendering once before a successful registration can target its new row. */
    private boolean suppressBrowseSearchListener;

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
                                     WorkspaceMembershipRequestService membershipRequestService,
                                     WorkspaceSelectionService selectionService,
                                     WorkspaceProperties workspaceProperties) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.participantService = participantService;
        this.membershipRequestService = membershipRequestService;
        this.selectionService = selectionService;
        this.workspaceProperties = workspaceProperties;

        browseSearch.setClearButtonVisible(true);
        browseSearch.setWidthFull();
        browseSearch.setValueChangeMode(ValueChangeMode.LAZY);
        browseSearch.addValueChangeListener(event -> {
            if (!suppressBrowseSearchListener) {
                // A new filter is a new result set, so accumulated pages no longer mean anything.
                loadedPages = 1;
                renderBrowseList(event.getValue());
            }
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

        if (canCreate()) {
            tabs = new TabSheet();
            tabs.setWidthFull();
            tabs.add(localization.i18n("participants.tab.browse"), browseTab());
            tabs.add(localization.i18n("participants.tab.add"), addTab());
            tabs.add(localization.i18n("participants.tab.requests"), requestsTab());
            tabs.setSelectedIndex(Math.min(selectedTabIndex, 2));
            tabs.addSelectedChangeListener(event -> selectedTabIndex = tabs.getSelectedIndex());
            content.add(tabs);
        } else {
            tabs = null;
            selectedTabIndex = 0;
            content.add(browseTab());
        }

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
        renderBrowseList(query, null);
    }

    /** Renders the requested roster and optionally reveals the participant that was just registered. */
    private void renderBrowseList(String query, UniqueId scrollTarget) {
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
        shown.forEach(participant -> {
            var body = addItem(participant);
            if (scrollTarget != null && scrollTarget.equals(participant.getUniqueId())) {
                body.getElement().executeJs(
                        "this.closest('.disclosure-item').scrollIntoView({behavior:'smooth',block:'center'})");
            }
        });
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
        more.addThemeName(RGButtonTheme.BORDERED);
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
    private Div addItem(WorkspaceParticipantModel participant) {
        var body = browseList.addItem(participant.getUniqueId(), participant.getLabel(), null,
                phoneMarker(participant));
        fillDetailPanel(body, participant);
        return body;
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
     * The expanded panel: reveal and call actions when there is a number to reveal, then edit and
     * remove. Management actions live in here rather than in the row, so they are shown on intent —
     * the arrangement the locations screen uses.
     */
    private void fillDetailPanel(Div body, WorkspaceParticipantModel participant) {
        if (participant.isPhoneRecorded()
                && authorityChecker.hasAuthority(participant.getUniqueId(),
                        LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT)) {
            var reveal = new Button(localization.i18n("participants.reveal"), VaadinIcon.EYE.create(),
                    event -> revealContact(participant));
            reveal.addThemeVariants(ButtonVariant.TERTIARY);
            reveal.addThemeName(RGButtonTheme.BORDERED);
            /* Commented because it is not working on IOS
            var call = new Button(localization.i18n("participants.call"), VaadinIcon.PHONE.create(),
                    event -> callContact(participant));
            call.addThemeVariants(ButtonVariant.TERTIARY);
            call.addThemeName(RGButtonTheme.BORDERED);
            var contactActions = new Div(reveal, call);
             */

            var contactActions = new Div(reveal);
            contactActions.addClassName("participant-contact-actions");
            body.add(contactActions);
        }

        var actions = new Div();
        actions.addClassName(DisclosureList.ROW_ACTIONS);
        if (authorityChecker.hasAuthority(participant.getUniqueId(),
                LocalPermissions.WorkspaceParticipant.UPDATE)) {
            var edit = new Button(localization.i18n("participants.edit"), VaadinIcon.EDIT.create(),
                    event -> openEditDialog(participant));
            edit.addThemeVariants(ButtonVariant.TERTIARY);
            edit.addThemeName(RGButtonTheme.BORDERED);
            actions.add(edit);
        }
        if (authorityChecker.hasAuthority(participant.getUniqueId(),
                LocalPermissions.WorkspaceParticipant.DELETE)) {
            var remove = new Button(localization.i18n("participants.remove"), VaadinIcon.TRASH.create(),
                    event -> confirmRemove(participant));
            remove.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            remove.addThemeName(RGButtonTheme.BORDERED);
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

        var label = labelField();
        var phone = phoneField();
        var save = new Button(localization.i18n("participants.register"), event -> {
            if (register(label.getValue(), phone.getValue())) {
                label.clear();
                phone.clear();
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addThemeName(RGButtonTheme.BORDERED);
        save.setWidthFull();

        var form = new Div(label, phone, save);
        form.addClassName("stacked-form");
        form.setWidthFull();
        layout.add(new Paragraph(localization.i18n("participants.add.hint")), form);
        return layout;
    }

    private Component requestsTab() {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        var requests = membershipRequestService.pending(workspaceId);
        if (requests.isEmpty()) {
            layout.add(new Paragraph(localization.i18n("participants.requests.empty")));
            return layout;
        }
        requests.forEach(request -> layout.add(requestRow(request)));
        return layout;
    }

    private Component requestRow(WorkspaceMembershipRequestModel request) {
        var requesterLabel = descriptorLabel(request);
        var name = requesterLabel == null || requesterLabel.isBlank()
                ? localization.i18n("participants.requests.unnamed") : requesterLabel;
        var title = new Paragraph(name);
        var approve = new Button(localization.i18n("participants.requests.approve"), VaadinIcon.CHECK.create(),
                ignored -> openApprovalDialog(request));
        approve.addThemeName(RGButtonTheme.BORDERED);
        var reject = new Button(localization.i18n("participants.requests.reject"), VaadinIcon.CLOSE_SMALL.create(),
                ignored -> reject(request));
        reject.addThemeVariants(ButtonVariant.ERROR);
        reject.addThemeName(RGButtonTheme.BORDERED);
        var row = new Div(title, approve, reject);
        row.addClassName("participant-membership-request");
        return row;
    }

    Dialog openApprovalDialog(WorkspaceMembershipRequestModel request) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("participants.requests.approve.title"));
        var target = new RadioButtonGroup<BindingTarget>(localization.i18n("participants.requests.approve.target"));
        target.setItems(BindingTarget.values());
        target.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        target.setItemLabelGenerator(value -> localization.i18n(value == BindingTarget.CREATE
                ? "participants.requests.approve.create" : "participants.requests.approve.existing"));
        target.setValue(BindingTarget.CREATE);
        var existing = new ComboBox<WorkspaceParticipantModel>(localization.i18n("participants.requests.approve.participant"));
        existing.setPageSize(BROWSE_PAGE_SIZE);
        existing.setWidthFull();
        existing.setItemLabelGenerator(WorkspaceParticipantModel::getLabel);
        existing.setItems(this::fetchExistingParticipantChoices, this::countExistingParticipantChoices);
        existing.setVisible(false);
        var label = labelField();
        var requesterLabel = descriptorLabel(request);
        label.setValue(requesterLabel == null ? "" : requesterLabel);
        var validRequesterLabel = requesterLabel != null
                && requesterLabel.trim().length() <= workspaceProperties.participantLabelMaxLength()
                && !requesterLabel.isBlank();
        label.setRequiredIndicatorVisible(!validRequesterLabel);
        target.addValueChangeListener(change -> {
            var useExisting = change.getValue() == BindingTarget.EXISTING;
            existing.setVisible(useExisting);
            label.setVisible(!useExisting);
        });
        var form = new FormLayout();
        form.setAutoResponsive(true);
        form.setExpandColumns(true);
        form.setExpandFields(true);
        form.addFormRow(target);
        form.addFormRow(existing);
        form.addFormRow(label);
        var approve = new Button(localization.i18n("participants.requests.approve"), ignored -> {
            try {
                var approvalTarget = target.getValue() == BindingTarget.EXISTING
                        ? WorkspaceMembershipApprovalTarget.BIND_EXISTING_PARTICIPANT
                        : WorkspaceMembershipApprovalTarget.CREATE_PARTICIPANT;
                if (approvalTarget == WorkspaceMembershipApprovalTarget.BIND_EXISTING_PARTICIPANT
                        && existing.getValue() == null) {
                    existing.setInvalid(true);
                    existing.setErrorMessage(localization.i18n("workspace.membership-request.error.participant-required"));
                    return;
                }
                existing.setInvalid(false);
                membershipRequestService.approve(workspaceId, request.getUniqueId(), approvalTarget,
                        existing.getValue() == null ? null : existing.getValue().getUniqueId(),
                        label.getValue());
                dialog.close();
                Notification.show(localization.i18n("participants.requests.approved"));
                render();
            } catch (IllegalArgumentException invalid) {
                Notification.show(localization.i18n(invalid.getMessage()));
            } catch (RuntimeException failure) {
                Notification.show(localization.i18n(failure));
            }
        });
        approve.addThemeName(RGButtonTheme.BORDERED);
        var cancel = new Button(localization.i18n("participants.cancel"), ignored -> dialog.close());
        cancel.addThemeName(RGButtonTheme.BORDERED);
        dialog.add(form);
        dialog.getFooter().add(cancel, approve);
        dialog.open();
        return dialog;
    }

    private void reject(WorkspaceMembershipRequestModel request) {
        try {
            membershipRequestService.reject(workspaceId, request.getUniqueId());
            Notification.show(localization.i18n("participants.requests.rejected"));
            render();
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
        }
    }

    private enum BindingTarget {CREATE, EXISTING}

    /** Fetches only the first fifty eligible, unbound candidates for the ComboBox's typed filter. */
    private Stream<WorkspaceParticipantModel> fetchExistingParticipantChoices(
            Query<WorkspaceParticipantModel, String> query) {
        var offset = query.getOffset();
        var limit = query.getLimit();
        if (offset >= BROWSE_PAGE_SIZE) {
            return Stream.empty();
        }
        return participantService.browseUnbound(workspaceId, normalizedFilter(query),
                        PageRequest.of(0, BROWSE_PAGE_SIZE))
                .getContent().stream()
                .skip(offset)
                .limit(Math.min(limit, BROWSE_PAGE_SIZE - offset));
    }

    /** Caps the virtual list so the dialog never requests candidates beyond its first fifty. */
    private int countExistingParticipantChoices(Query<WorkspaceParticipantModel, String> query) {
        var total = participantService.browseUnbound(workspaceId, normalizedFilter(query), PageRequest.of(0, 1))
                .getTotalElements();
        return Math.toIntExact(Math.min(total, BROWSE_PAGE_SIZE));
    }

    private static String normalizedFilter(Query<WorkspaceParticipantModel, String> query) {
        return query.getFilter().orElse("").trim();
    }

    private boolean register(String label, String phone) {
        WorkspaceParticipantModel created;
        try {
            created = participantService.register(workspaceId, ParticipantDescriptor.of(label, phone));
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
        afterCreate(created);
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
        close.addThemeName(RGButtonTheme.BORDERED);
        try {
            var revealed = participantService.revealContact(participant.getUniqueId());
            if (revealed.phone() == null || revealed.phone().isBlank()) {
                dialog.add(new Paragraph(localization.i18n("participants.phone.unavailable")));
            } else {
                dialog.add(revealedContact(revealed.phone()));
            }
        } catch (RuntimeException failure) {
            // Never the exception's own text: it may have come from a layer that saw the value.
            dialog.add(new Paragraph(localization.i18n(failure)));
        }
        dialog.getFooter().add(close);
        dialog.open();
        return new Prompt(dialog, close, close);
    }

    private void callContact(WorkspaceParticipantModel participant) {
        try {
            var revealed = participantService.revealContact(participant.getUniqueId());
            if (revealed.phone() == null || revealed.phone().isBlank()) {
                Notification.show(localization.i18n("participants.phone.unavailable"));
                return;
            }
            getElement().executeJs("window.location.href = $0", "tel:" + revealed.phone());
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
        }
    }

    private Div revealedContact(String phone) {
        var number = new Span(phone);
        number.addClassName("participant-revealed-phone");
        var copy = new Button(VaadinIcon.COPY.create());
        var copyLabel = localization.i18n("participants.copy");
        copy.setAriaLabel(copyLabel);
        copy.getElement().setAttribute("title", copyLabel);
        Clipboard.onClick(copy).writeText(phone,
                copied -> animateCopiedPhone(number),
                error -> Notification.show(localization.i18n("participants.copy.failed")));
        var revealedContact = new Div(number, copy);
        revealedContact.addClassName("participant-revealed-contact");

        return revealedContact;
    }

    private void animateCopiedPhone(Span number) {
        number.getElement().executeJs("""
                this.classList.remove('participant-revealed-phone--copied');
                void this.offsetWidth;
                this.classList.add('participant-revealed-phone--copied');
                window.Telegram?.WebApp?.HapticFeedback?.notificationOccurred('success');
                """);
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
        // Pre-filled only for a caller who may see the number anyway. Saving replaces the whole
        // descriptor, so without the current value in the field an edit of the label alone would wipe
        // the number -- which is why the two cases carry different helper text.
        var current = currentNumberIfPermitted(participant);
        if (current == null) {
            phone.setHelperText(localization.i18n("participant.field.phone.edit-helper"));
        } else {
            phone.setValue(current);
            phone.setHelperText(localization.i18n("participant.field.phone.edit-helper-filled"));
        }

        var save = new Button(localization.i18n("participants.save"), event -> {
            if (update(participant, label.getValue(), phone.getValue())) {
                dialog.close();
            }
        });
        save.addThemeName(RGButtonTheme.BORDERED);
        var cancel = new Button(localization.i18n("participants.cancel"), event -> dialog.close());
        cancel.addThemeName(RGButtonTheme.BORDERED);
        dialog.getFooter().add(cancel, save);
        dialog.add(label, phone);
        dialog.open();
        return new Prompt(dialog, save, cancel);
    }

    /**
     * The participant's number, for pre-filling the edit form — or {@code null} when the caller may not
     * see it.
     *
     * <p>Gated on {@code reveal-contact}, the same permission the roster's reveal action carries, so
     * editing cannot become a way around it. <strong>Note that opening the edit form is therefore a
     * disclosure</strong>: it calls {@code revealContact} exactly as the reveal button does. If reveals
     * are ever audited, this counts as one.
     *
     * <p>Failure is not fatal — a caller who cannot fetch the number can still edit the label — so the
     * form falls back to the empty field and its warning helper text. Nothing about the failure is
     * logged beyond its type: this is the one path that handles plaintext.
     */
    private String currentNumberIfPermitted(WorkspaceParticipantModel participant) {
        if (!participant.isPhoneRecorded() || !authorityChecker.hasAuthority(
                participant.getUniqueId(), LocalPermissions.WorkspaceParticipant.REVEAL_CONTACT)) {
            return null;
        }
        try {
            var revealed = participantService.revealContact(participant.getUniqueId());
            return revealed == null ? null : revealed.phone();
        } catch (RuntimeException failure) {
            log.debug("Could not pre-fill a participant's number: {}",
                    failure.getClass().getSimpleName());
            return null;
        }
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

    /** Returns from successful registration to the matching Browse result and reveals its row. */
    private void afterCreate(WorkspaceParticipantModel created) {
        loadedPages = 1;
        selectedTabIndex = 0;
        if (tabs != null) {
            tabs.setSelectedIndex(0);
        }
        suppressBrowseSearchListener = true;
        try {
            browseSearch.setValue(created.getLabel());
        } finally {
            suppressBrowseSearchListener = false;
        }
        try {
            renderBrowseList(created.getLabel(), created.getUniqueId());
        } catch (RuntimeException refreshFailed) {
            log.debug("Participant roster refresh failed after successful registration", refreshFailed);
        }
    }

    private boolean canCreate() {
        return authorityChecker.hasAuthority(workspaceId, LocalPermissions.WorkspaceParticipant.CREATE);
    }

    private static String descriptorLabel(WorkspaceMembershipRequestModel request) {
        return request.getDescriptor() == null ? null : request.getDescriptor().label();
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
}
