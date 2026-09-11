package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.component.location.LocationPicker;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.rg.service.workspace.WorkspaceLocationService;
import vg.rg.service.workspace.event.WorkspaceEventService;
import vg.unique.id.model.UniqueId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceEventsViewTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceEventService eventService;
    @Mock WorkspaceLocationService locationService;
    @Mock WorkspaceSelectionService selectionService;
    @Mock BeforeEnterEvent event;

    private UI ui;

    @BeforeEach
    void attachUi() {
        ui = new UI();
        UI.setCurrent(ui);
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    @Test
    void rendersParticipantStyleTabsAndAResponsiveEventForm() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.newDateTimePicker(anyString(), any(DateDisplayOptions.class))).thenCallRealMethod();
        when(localization.getTranslation(anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getTranslation(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(authorityChecker.hasAuthority(any(UniqueId.class), anyString())).thenReturn(true);
        when(selectionService.activeWorkspace()).thenReturn(WorkspaceModel.builder().uniqueId(new UniqueId(8101L)).build());
        when(eventService.browse(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(WorkspaceEventModel.builder()
                        .uniqueId(new UniqueId(8102L)).title("Planning")
                        .startAt(java.time.Instant.parse("2026-09-13T09:00:00Z"))
                        .eventType(WorkspaceEventType.PADEL).maxParticipantCount(8).isPublished(true).build())));
        when(locationService.browse(any(), any())).thenReturn(new PageImpl<>(List.of()));

        var view = new WorkspaceEventsView(localization, authorityChecker, eventService, locationService, selectionService);
        view.beforeEnter(event);

        var tabs = descendants(view).filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();
        assertThat(tabs).isNotNull();
        tabs.setSelectedIndex(1);
        var form = descendants(view).filter(FormLayout.class::isInstance).map(FormLayout.class::cast)
                .findFirst().orElseThrow();
        assertThat(form.getElement().getProperty("autoResponsive", false)).isTrue();
        assertThat(form.getChildren()).hasSize(7);
        assertThat(dateTimePickers(form)).allSatisfy(field ->
                assertThat(field.getValue()).isEqualTo(LocalDate.now(ZoneOffset.UTC).atTime(LocalTime.NOON)));
        assertThat(dateTimePickers(form)).allSatisfy(field ->
                assertThat(field.getStep()).isEqualTo(Duration.ofMinutes(15)));
        assertThat(dateTimePickers(form)).allSatisfy(field ->
                assertThat(field.getLabel()).matches("event\\.field\\.(start-at|end-at) \\(.+\\)"));
        assertThat(maxParticipantCountFields(form)).singleElement()
                .extracting(IntegerField::getValue).isEqualTo(24);
        var locationPickers = descendants(form).filter(LocationPicker.class::isInstance).map(LocationPicker.class::cast)
                .toList();
        assertThat(locationPickers).singleElement().satisfies(
                locationPicker -> assertThat(locationPicker.isRequiredIndicatorVisible()).isTrue());
    }

    @Test
    void withoutCreatePermission_rendersBrowseDirectlyWithoutTabCaptions() {
        arrange();
        when(authorityChecker.hasAuthority(any(UniqueId.class), anyString())).thenReturn(false);

        var view = new WorkspaceEventsView(localization, authorityChecker, eventService, locationService, selectionService);
        view.beforeEnter(event);

        assertThat(descendants(view).filter(TabSheet.class::isInstance)).isEmpty();
        assertThat(buttonTexts(view)).doesNotContain("events.create");
    }

    @Test
    void creation_switchesToBrowseFiltersBySavedTitleAndShowsItsRow() {
        arrange();
        var created = WorkspaceEventModel.builder()
                .uniqueId(new UniqueId(8103L)).title("Retrospective")
                .startAt(java.time.Instant.parse("2026-09-13T09:00:00Z"))
                .eventType(WorkspaceEventType.TENNIS).maxParticipantCount(10).isPublished(false).build();
        when(eventService.create(any(), any())).thenReturn(created);

        var view = new WorkspaceEventsView(localization, authorityChecker, eventService, locationService, selectionService);
        view.beforeEnter(event);
        when(eventService.browse(any(), org.mockito.ArgumentMatchers.eq("Retrospective"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(created)));

        descendants(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> "event.field.title".equals(field.getLabel()))
                .findFirst().orElseThrow().setValue("Retrospective");
        dateTimePickers(view).getFirst().setValue(LocalDateTime.of(2026, 9, 13, 9, 0));
        radioGroups(view).getFirst().setValue(WorkspaceEventType.TENNIS);
        maxParticipantCountFields(view).getFirst().setValue(10);
        click(descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "events.create".equals(button.getText())).findFirst().orElseThrow());

        var tabs = descendants(view).filter(TabSheet.class::isInstance).map(TabSheet.class::cast)
                .findFirst().orElseThrow();
        assertThat(tabs.getSelectedIndex()).isZero();
        assertThat(rowNames(view)).containsExactly("Retrospective");
        org.mockito.Mockito.verify(eventService).browse(any(), org.mockito.ArgumentMatchers.eq("Retrospective"), any(Pageable.class));
    }

    @Test
    void creation_passesTheScheduleTypeAndSelectedLocation() {
        arrange();
        var location = LocationModel.builder().uniqueId(new UniqueId(8104L)).name("Central courts").build();
        var created = WorkspaceEventModel.builder().uniqueId(new UniqueId(8103L)).title("Retrospective")
                .startAt(java.time.Instant.parse("2026-09-13T09:00:00Z"))
                .eventType(WorkspaceEventType.TENNIS).locationUniqueId(location.getUniqueId())
                .maxParticipantCount(10).build();
        when(eventService.create(any(), any())).thenReturn(created);
        when(locationService.searchByName(any(), eq("Central courts"), any(Integer.class)))
                .thenReturn(List.of(location));
        var view = new WorkspaceEventsView(localization, authorityChecker, eventService, locationService, selectionService);
        view.beforeEnter(event);

        descendants(view).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> "event.field.title".equals(field.getLabel())).findFirst().orElseThrow()
                .setValue("Retrospective");
        var dateTimePickers = dateTimePickers(view);
        dateTimePickers.getFirst().setValue(LocalDateTime.of(2026, 9, 13, 9, 0));
        dateTimePickers.get(1).clear();
        radioGroups(view).getFirst().setValue(WorkspaceEventType.TENNIS);
        maxParticipantCountFields(view).getFirst().setValue(10);
        descendants(view).filter(LocationPicker.class::isInstance).map(LocationPicker.class::cast)
                .findFirst().orElseThrow().setValue(location);
        click(descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "events.create".equals(button.getText())).findFirst().orElseThrow());

        verify(eventService).create(any(), argThat(model -> "Retrospective".equals(model.getTitle())
                && java.time.Instant.parse("2026-09-13T09:00:00Z").equals(model.getStartAt())
                && model.getEndAt() == null && model.getEventType() == WorkspaceEventType.TENNIS
                && model.getMaxParticipantCount() == 10
                && location.getUniqueId().equals(model.getLocationUniqueId())));
    }

    @Test
    void validationError_marksTheDateTimePickerUntilTheValueIsChanged() {
        arrange();
        when(eventService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("workspace.event.error.start-required"));
        var view = new WorkspaceEventsView(localization, authorityChecker, eventService, locationService, selectionService);
        view.beforeEnter(event);

        var startAt = dateTimePickers(view).getFirst();
        click(descendants(view).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "events.create".equals(button.getText())).findFirst().orElseThrow());

        assertThat(startAt.isInvalid()).isTrue();
        assertThat(startAt.getErrorMessage()).isEqualTo("workspace.event.error.start-required");

        startAt.setValue(startAt.getValue().plusMinutes(30));

        assertThat(startAt.isInvalid()).isFalse();
    }

    private void arrange() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.newDateTimePicker(anyString(), any(DateDisplayOptions.class))).thenCallRealMethod();
        when(localization.getTranslation(anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getTranslation(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(authorityChecker.hasAuthority(any(UniqueId.class), anyString())).thenReturn(true);
        when(selectionService.activeWorkspace()).thenReturn(WorkspaceModel.builder().uniqueId(new UniqueId(8101L)).build());
        when(eventService.browse(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(WorkspaceEventModel.builder()
                        .uniqueId(new UniqueId(8102L)).title("Planning")
                        .startAt(java.time.Instant.parse("2026-09-13T09:00:00Z"))
                        .eventType(WorkspaceEventType.PADEL).maxParticipantCount(8).isPublished(true).build())));
        when(locationService.browse(any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    private static Stream<Component> descendants(Component component) {
        var children = new java.util.ArrayList<>(component.getChildren().toList());
        if (component instanceof TabSheet tabs) {
            for (int index = 0; ; index++) {
                try {
                    var content = tabs.getComponent(tabs.getTabAt(index));
                    if (content != null) children.add(content);
                } catch (RuntimeException outOfRange) {
                    break;
                }
            }
        }
        return children.stream().flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }

    private static void click(Component component) {
        ComponentUtil.fireEvent(component, new ClickEvent<>(component));
    }

    private static List<String> buttonTexts(Component component) {
        return descendants(component).filter(Button.class::isInstance).map(Button.class::cast)
                .map(Button::getText).toList();
    }

    private static List<String> rowNames(Component component) {
        return descendants(component).filter(com.vaadin.flow.component.html.Span.class::isInstance)
                .map(com.vaadin.flow.component.html.Span.class::cast)
                .filter(span -> span.hasClassName(vg.rg.frontend.vaadin.component.disclosure.DisclosureList.ROW_NAME))
                .map(com.vaadin.flow.component.html.Span::getText).toList();
    }

    private static List<DateTimePicker> dateTimePickers(Component component) {
        return descendants(component).filter(DateTimePicker.class::isInstance).map(DateTimePicker.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<RadioButtonGroup<WorkspaceEventType>> radioGroups(Component component) {
        return descendants(component).filter(RadioButtonGroup.class::isInstance)
                .map(group -> (RadioButtonGroup<WorkspaceEventType>) group).toList();
    }

    private static List<IntegerField> maxParticipantCountFields(Component component) {
        return descendants(component).filter(IntegerField.class::isInstance).map(IntegerField.class::cast).toList();
    }
}
