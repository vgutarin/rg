package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
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
import com.vaadin.flow.component.textfield.IntegerField;
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
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.frontend.vaadin.component.dialog.Dialogs;
import vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions;
import vg.rg.frontend.vaadin.component.datetime.DateTimes;
import vg.rg.frontend.vaadin.component.disclosure.DisclosureList;
import vg.rg.frontend.vaadin.component.location.LocationPicker;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.rg.service.workspace.event.WorkspaceEventService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;

/** Owner-managed events in the active workspace. */
@Slf4j
@PageTitle("page.workspace-events.title")
@Route(value = "workspaces/events", layout = WorkspaceLayout.class)
@PermitAll
public class WorkspaceEventsView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    static final int BROWSE_PAGE_SIZE = 20;
    private static final ZoneOffset EVENT_TIME_ZONE = ZoneOffset.UTC;
    private static final DateDisplayOptions EVENT_DATE_TIME_OPTIONS = DateDisplayOptions.builder()
            .showShortDayName(true)
            .build();
    private static final int DEFAULT_MAX_PARTICIPANT_COUNT = 24;
    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient WorkspaceEventService eventService;
    private final transient WorkspaceLocationService locationService;
    private final transient WorkspaceSelectionService selectionService;
    private final Div content = new Div();
    private final DisclosureList browseList = new DisclosureList();
    private final TextField browseSearch = new TextField();
    private final Div browseFooter = new Div();
    private TabSheet tabs;
    private boolean suppressBrowseSearchListener;
    private int loadedPages = 1;
    private int selectedTabIndex;
    private UniqueId workspaceId;

    public WorkspaceEventsView(LocalizationService localization, AuthorityChecker authorityChecker,
                               WorkspaceEventService eventService, WorkspaceLocationService locationService,
                               WorkspaceSelectionService selectionService) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.eventService = eventService;
        this.locationService = locationService;
        this.selectionService = selectionService;
        browseSearch.setClearButtonVisible(true);
        browseSearch.setWidthFull();
        browseSearch.setValueChangeMode(ValueChangeMode.LAZY);
        browseSearch.addValueChangeListener(event -> {
            if (!suppressBrowseSearchListener) {
                loadedPages = 1;
                renderBrowseList(event.getValue());
            }
        });
        addClassName("secure-view");
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            event.rerouteTo(NoAccessView.class);
            return;
        }
        var active = selectionService.activeWorkspace();
        workspaceId = active == null ? null : active.getUniqueId();
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (workspaceId != null && authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) render();
        else content.removeAll();
    }

    private void render() {
        content.removeAll();
        if (workspaceId == null) return;
        content.setWidthFull();
        content.addClassNames("semantic-card", "aura-surface");
        content.add(new H1(localization.i18n("page.workspace-events.title")));
        if (canCreate()) {
            tabs = new TabSheet();
            tabs.setWidthFull();
            tabs.add(localization.i18n("events.tab.browse"), browseTab());
            tabs.add(localization.i18n("events.tab.add"), addTab());
            tabs.setSelectedIndex(Math.min(selectedTabIndex, 1));
            tabs.addSelectedChangeListener(event -> selectedTabIndex = tabs.getSelectedIndex());
            content.add(tabs);
        } else {
            tabs = null;
            selectedTabIndex = 0;
            content.add(browseTab());
        }
        renderBrowseList(browseSearch.getValue());
    }

    private Component browseTab() {
        browseSearch.setPlaceholder(localization.i18n("events.search.placeholder"));
        browseFooter.addClassName("browse-footer");
        var layout = new VerticalLayout(browseSearch, browseList, browseFooter);
        layout.setPadding(false); layout.setSpacing(false); layout.setWidthFull();
        return layout;
    }

    private void renderBrowseList(String query) {
        renderBrowseList(query, null);
    }

    private void renderBrowseList(String query, UniqueId scrollTarget) {
        browseList.reset(); browseFooter.removeAll();
        var filter = query == null ? "" : query.trim();
        var page = eventService.browse(workspaceId, filter, PageRequest.of(0, loadedPages * BROWSE_PAGE_SIZE,
                Sort.by("title").ascending().and(Sort.by("uniqueId").ascending())));
        if (page.isEmpty()) {
            browseList.add(new Paragraph(localization.i18n(filter.isBlank() ? "events.empty" : "events.search.no-results")));
            return;
        }
        page.forEach(event -> {
            var body = addItem(event);
            if (scrollTarget != null && scrollTarget.equals(event.getUniqueId())) {
                body.getElement().executeJs(
                        "this.closest('.disclosure-item').scrollIntoView({behavior:'smooth',block:'center'})");
            }
        });
        if (page.getNumberOfElements() < page.getTotalElements()) {
            var count = new Paragraph(localization.getTranslation("events.showing", localization.getCurrentLocale(),
                    page.getNumberOfElements(), page.getTotalElements()));
            count.addClassName("browse-count");
            var more = new Button(localization.i18n("events.load-more"), event -> { loadedPages++; renderBrowseList(browseSearch.getValue()); });
            more.setWidthFull();
            browseFooter.add(count, more);
        }
    }

    private Div addItem(WorkspaceEventModel event) {
        var body = browseList.addItem(event.getUniqueId(), event.getTitle(), null);
        body.add(DisclosureList.detailRow(VaadinIcon.FLAG, localization.i18n("event.field.type"),
                localization.i18n(eventTypeKey(event.getEventType())), false));
        body.add(DisclosureList.detailRow(VaadinIcon.CALENDAR, localization.i18n("event.field.start-at"),
                formatTime(event.getStartAt()), false));
        if (event.getEndAt() != null) {
            body.add(DisclosureList.detailRow(VaadinIcon.CALENDAR, localization.i18n("event.field.end-at"),
                    formatTime(event.getEndAt()), false));
        }
        if (event.getMaxParticipantCount() != null) {
            body.add(DisclosureList.detailRow(VaadinIcon.USER, localization.i18n("event.field.max-participant-count"),
                    event.getMaxParticipantCount().toString(), false));
        }
        var status = new Span(localization.i18n(event.isPublished() ? "events.status.published" : "events.status.draft"));
        status.addClassName(event.isPublished() ? "event-status-published" : "event-status-draft");
        body.add(status);
        var actions = new Div(); actions.addClassName(DisclosureList.ROW_ACTIONS);
        if (authorityChecker.hasAuthority(event.getUniqueId(), LocalPermissions.WorkspaceEvent.UPDATE)) {
            var edit = new Button(localization.i18n("events.edit"), VaadinIcon.EDIT.create(), ignored -> openEdit(event));
            edit.addThemeVariants(ButtonVariant.TERTIARY); actions.add(edit);
        }
        if (authorityChecker.hasAuthority(event.getUniqueId(), LocalPermissions.WorkspaceEvent.DELETE)) {
            var delete = new Button(localization.i18n("events.remove"), VaadinIcon.TRASH.create(), ignored -> confirmDelete(event));
            delete.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR); actions.add(delete);
        }
        if (actions.getElement().getChildCount() > 0) body.add(actions);
        return body;
    }

    private Component addTab() {
        var layout = new VerticalLayout(); layout.setPadding(false); layout.setSpacing(false); layout.setWidthFull();
        var fields = eventFields(WorkspaceEventModel.builder().build());
        var save = new Button(localization.i18n("events.create"), ignored -> {
            if (create(fields)) {
                fields.title().clear();
                fields.startAt().setValue(defaultDateTime());
                fields.endAt().setValue(defaultDateTime());
                fields.location().clear();
                fields.eventType().clear();
                fields.maxParticipantCount().setValue(DEFAULT_MAX_PARTICIPANT_COUNT);
                fields.published().setValue(false);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY); save.setWidthFull();
        layout.add(new Paragraph(localization.i18n("events.add.hint")), form(fields), save);
        return layout;
    }

    FormLayout form(EventFields fields) {
        var form = new FormLayout();
        form.setWidthFull();
        form.setAutoResponsive(true);
        form.setExpandColumns(true);
        form.setExpandFields(true);
        form.addFormRow(fields.title());
        form.addFormRow(fields.startAt());
        form.addFormRow(fields.endAt());
        form.addFormRow(fields.location());
        form.addFormRow(fields.eventType());
        form.addFormRow(fields.maxParticipantCount());
        form.addFormRow(fields.published());
        return form;
    }

    private boolean create(EventFields fields) {
        try {
            var created = eventService.create(
                    workspaceId,
                    WorkspaceEventModel.builder()
                            .title(fields.title().getValue())
                            .startAt(toInstant(fields.startAt().getValue()))
                            .endAt(toInstant(fields.endAt().getValue()))
                            .locationUniqueId(fields.location().getValue() == null
                                    ? null : fields.location().getValue().getUniqueId())
                            .eventType(fields.eventType().getValue())
                            .maxParticipantCount(fields.maxParticipantCount().getValue())
                            .isPublished(fields.published().getValue())
                            .build()
            );
            Notification.show(localization.i18n("events.created")); afterCreate(created); return true;
        } catch (IllegalArgumentException invalid) { showValidationError(fields, invalid.getMessage());
        } catch (RuntimeException failure) { Notification.show(localization.i18n(failure)); }
        return false;
    }

    void openEdit(WorkspaceEventModel event) {
        var dialog = new Dialog(); dialog.setHeaderTitle(localization.i18n("events.edit.title"));
        var fields = eventFields(event);
        var save = new Button(localization.i18n("events.save"), ignored -> { if (update(event, fields)) dialog.close(); });
        var cancel = new Button(localization.i18n("events.cancel"), ignored -> dialog.close());
        dialog.add(form(fields)); dialog.getFooter().add(cancel, save); dialog.open();
    }

    private boolean update(WorkspaceEventModel current, EventFields fields) {
        try {
            eventService.update(WorkspaceEventModel.builder().uniqueId(current.getUniqueId()).version(current.getVersion())
                    .title(fields.title().getValue())
                    .startAt(toInstant(fields.startAt().getValue()))
                    .endAt(toInstant(fields.endAt().getValue()))
                    .locationUniqueId(fields.location().getValue() == null
                            ? null : fields.location().getValue().getUniqueId())
                    .eventType(fields.eventType().getValue())
                    .maxParticipantCount(fields.maxParticipantCount().getValue())
                    .isPublished(fields.published().getValue()).build());
            afterChange(); return true;
        } catch (ObjectOptimisticLockingFailureException stale) { Notification.show(localization.i18n("events.stale"));
        } catch (IllegalArgumentException invalid) { showValidationError(fields, invalid.getMessage());
        } catch (RuntimeException failure) { Notification.show(localization.i18n(failure)); }
        return false;
    }

    void confirmDelete(WorkspaceEventModel event) {
        Dialogs.confirmDeletion(localization, "events.remove", "events.remove.confirm", "events.remove", "events.cancel", () -> remove(event));
    }

    private void remove(WorkspaceEventModel event) {
        try { eventService.delete(event.getUniqueId()); Notification.show(localization.i18n("events.removed")); afterChange();
        } catch (RuntimeException failure) { Notification.show(localization.i18n(failure)); }
    }

    private TextField titleField() {
        var field = new TextField(localization.i18n("event.field.title"));
        field.setWidthFull(); field.setRequiredIndicatorVisible(true); field.setMaxLength(512);
        field.addValueChangeListener(event -> field.setInvalid(false));
        return field;
    }

    private Checkbox publishedField(boolean value) {
        var field = new Checkbox(localization.i18n("event.field.published")); field.setValue(value); return field;
    }

    private EventFields eventFields(WorkspaceEventModel event) {
        var title = titleField();
        title.setValue(event.getTitle() == null ? "" : event.getTitle());
        var isNew = event.getUniqueId() == null;
        var startAt = dateTimePicker("event.field.start-at", true,
                isNew && event.getStartAt() == null ? defaultDateTime() : DateTimes.toLocal(event.getStartAt(), EVENT_TIME_ZONE));
        var endAt = dateTimePicker("event.field.end-at", false,
                isNew && event.getEndAt() == null ? defaultDateTime() : DateTimes.toLocal(event.getEndAt(), EVENT_TIME_ZONE));
        startAt.addValueChangeListener(ignored -> endAt.setInvalid(false));
        var location = new LocationPicker(localization, locationService);
        location.setLabel(localization.i18n("event.field.location"));
        location.setRequiredIndicatorVisible(true);
        location.addValueChangeListener(ignored -> location.setInvalid(false));
        location.setWorkspaceId(workspaceId);
        location.setSelectedLocationId(event.getLocationUniqueId());
        var eventType = eventTypeField(event.getEventType());
        var maxParticipantCount = maxParticipantCountField(event.getMaxParticipantCount());
        return new EventFields(title, startAt, endAt, location, eventType, maxParticipantCount,
                publishedField(event.isPublished()));
    }

    private DateTimePicker dateTimePicker(String labelKey, boolean required, LocalDateTime value) {
        var field = localization.newDateTimePicker(labelKey, EVENT_DATE_TIME_OPTIONS);
        field.setRequiredIndicatorVisible(required);
        field.setWidthFull();
        field.setValue(value);
        field.addValueChangeListener(event -> field.setInvalid(false));
        return field;
    }

    private RadioButtonGroup<WorkspaceEventType> eventTypeField(WorkspaceEventType value) {
        var field = new RadioButtonGroup<WorkspaceEventType>(localization.i18n("event.field.type"));
        field.setItems(WorkspaceEventType.values());
        field.setItemLabelGenerator(type -> localization.i18n(eventTypeKey(type)));
        field.addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
        field.setRequiredIndicatorVisible(true);
        field.setWidthFull();
        field.setValue(value);
        field.addValueChangeListener(event -> field.setInvalid(false));
        return field;
    }

    private IntegerField maxParticipantCountField(Integer value) {
        var field = new IntegerField(localization.i18n("event.field.max-participant-count"));
        field.setRequiredIndicatorVisible(true);
        field.setMin(1);
        field.setStepButtonsVisible(true);
        field.setWidthFull();
        field.setValue(value == null ? DEFAULT_MAX_PARTICIPANT_COUNT : value);
        field.addValueChangeListener(event -> field.setInvalid(false));
        return field;
    }

    private void showValidationError(EventFields fields, String messageKey) {
        Notification.show(localization.i18n(messageKey));
        switch (messageKey) {
            case "workspace.event.error.title-required", "workspace.event.error.title-too-long" -> markInvalid(fields.title(), messageKey);
            case "workspace.event.error.start-required" -> markInvalid(fields.startAt(), messageKey);
            case "workspace.event.error.end-before-start" -> markInvalid(fields.endAt(), messageKey);
            case "workspace.event.error.location-required", "workspace.event.error.location-outside-workspace" -> markInvalid(fields.location(), messageKey);
            case "workspace.event.error.type-required" -> markInvalid(fields.eventType(), messageKey);
            case "workspace.event.error.max-participant-count-required", "workspace.event.error.max-participant-count-not-positive" ->
                    markInvalid(fields.maxParticipantCount(), messageKey);
            default -> { }
        }
    }

    private void markInvalid(TextField field, String messageKey) {
        field.setErrorMessage(localization.i18n(messageKey));
        field.setInvalid(true);
        field.getElement().executeJs("this.focus()");
    }

    private void markInvalid(DateTimePicker field, String messageKey) {
        field.setErrorMessage(localization.i18n(messageKey));
        field.setInvalid(true);
        field.focus();
    }

    private void markInvalid(LocationPicker field, String messageKey) {
        field.setErrorMessage(localization.i18n(messageKey));
        field.setInvalid(true);
        field.focus();
    }

    private void markInvalid(RadioButtonGroup<WorkspaceEventType> field, String messageKey) {
        field.setErrorMessage(localization.i18n(messageKey));
        field.setInvalid(true);
        field.getElement().executeJs("this.focus()");
    }

    private void markInvalid(IntegerField field, String messageKey) {
        field.setErrorMessage(localization.i18n(messageKey));
        field.setInvalid(true);
        field.focus();
    }

    private static String eventTypeKey(WorkspaceEventType type) {
        return "event.type." + type.name().toLowerCase(Locale.ROOT);
    }

    private String formatTime(Instant value) {
        return DateTimes.format(DateTimes.toLocal(value, EVENT_TIME_ZONE), localization.getCurrentLocale(),
                EVENT_DATE_TIME_OPTIONS);
    }

    private static Instant toInstant(LocalDateTime value) {
        return DateTimes.toInstant(value, EVENT_TIME_ZONE);
    }

    record EventFields(TextField title, DateTimePicker startAt,
                       DateTimePicker endAt, LocationPicker location,
                       RadioButtonGroup<WorkspaceEventType> eventType, IntegerField maxParticipantCount,
                       Checkbox published) { }

    private static LocalDateTime defaultDateTime() {
        return LocalDate.now(EVENT_TIME_ZONE).atTime(LocalTime.NOON);
    }

    private void afterChange() {
        loadedPages = 1;
        try { renderBrowseList(browseSearch.getValue());
        } catch (RuntimeException failure) { log.debug("Event list refresh failed after a successful change", failure); }
    }

    private void afterCreate(WorkspaceEventModel created) {
        loadedPages = 1;
        selectedTabIndex = 0;
        if (tabs != null) {
            tabs.setSelectedIndex(0);
        }
        suppressBrowseSearchListener = true;
        try {
            browseSearch.setValue(created.getTitle());
        } finally {
            suppressBrowseSearchListener = false;
        }
        try {
            renderBrowseList(created.getTitle(), created.getUniqueId());
        } catch (RuntimeException failure) {
            log.debug("Event list refresh failed after successful creation", failure);
        }
    }

    private boolean canCreate() {
        return authorityChecker.hasAuthority(workspaceId, LocalPermissions.WorkspaceEvent.CREATE);
    }
}
