package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
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
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.workspace.WorkspaceEventModel;
import vg.rg.model.workspace.WorkspaceEventRegistrationModel;
import vg.rg.model.workspace.WorkspaceParticipantModel;
import vg.rg.service.workspace.WorkspaceParticipantService;
import vg.rg.service.workspace.event.WorkspaceEventRegistrationService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventParticipantsDialogTest {

    private static final UniqueId WORKSPACE = new UniqueId(9001L);
    private static final UniqueId EVENT = new UniqueId(9002L);

    @Mock LocalizationService localization;
    @Mock WorkspaceEventRegistrationService registrationService;
    @Mock WorkspaceParticipantService participantService;

    private UI ui;

    @BeforeEach
    void attachUi() {
        ui = new UI();
        UI.setCurrent(ui);
        when(localization.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.getTranslation(anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(localization.getTranslation(anyString(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(localization.getTranslation(anyString(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    private EventParticipantsDialog open() {
        var event = WorkspaceEventModel.builder().uniqueId(EVENT).title("Padel").maxParticipantCount(8).build();
        var dialog = new EventParticipantsDialog(
                localization, registrationService, participantService, WORKSPACE, event);
        dialog.open();
        return dialog;
    }

    @Test
    void registeredRoster_isShownInOrderAndAlreadyRegisteredAreExcludedFromAddList() {
        var alice = registration(new UniqueId(1L), new UniqueId(11L), "Alice", 0);
        var bob = registration(new UniqueId(2L), new UniqueId(12L), "Bob", 1);
        when(registrationService.list(EVENT)).thenReturn(List.of(alice, bob));
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                participant(new UniqueId(11L), "Alice"),
                participant(new UniqueId(13L), "Carol"))));

        var dialog = open();

        assertThat(dialog.registeredLabels()).containsExactly("Alice", "Bob");
        assertThat(dialog.availableLabels()).containsExactly("Carol");
    }

    @Test
    void registeredTab_captionCarriesTheCount() {
        var alice = registration(new UniqueId(1L), new UniqueId(11L), "Alice", 0);
        var bob = registration(new UniqueId(2L), new UniqueId(12L), "Bob", 1);
        when(registrationService.list(EVENT)).thenReturn(List.of(alice, bob));
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var dialog = open();

        // The stub echoes the key, so the count is not interpolated — assert the caption resolved to the
        // count-bearing key rather than the plain "registered" one.
        assertThat(dialog.registeredTabForTest().getLabel())
                .isEqualTo("events.participants.registered-tab");
    }

    @Test
    void register_callsTheServiceWithTheEventAndParticipant() {
        when(registrationService.list(EVENT)).thenReturn(List.of());
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                participant(new UniqueId(13L), "Carol"))));

        var dialog = open();
        var register = descendants(dialog.addResultsForTest())
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "events.participants.register".equals(button.getAriaLabel().orElse(null)))
                .findFirst().orElseThrow();
        ComponentUtil.fireEvent(register, new ClickEvent<>(register));

        verify(registrationService).register(EVENT, new UniqueId(13L));
    }

    @Test
    void reorderButtons_areDisabledAtTheEnds() {
        var alice = registration(new UniqueId(1L), new UniqueId(11L), "Alice", 0);
        var bob = registration(new UniqueId(2L), new UniqueId(12L), "Bob", 1);
        when(registrationService.list(EVENT)).thenReturn(List.of(alice, bob));
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var dialog = open();

        var upButtons = buttonsByAriaLabel(dialog, "events.participants.move-up");
        var downButtons = buttonsByAriaLabel(dialog, "events.participants.move-down");
        // First row cannot move up; last row cannot move down.
        assertThat(upButtons.get(0).isEnabled()).isFalse();
        assertThat(upButtons.get(1).isEnabled()).isTrue();
        assertThat(downButtons.get(0).isEnabled()).isTrue();
        assertThat(downButtons.get(1).isEnabled()).isFalse();
    }

    @Test
    void moveDown_callsTheService() {
        var alice = registration(new UniqueId(1L), new UniqueId(11L), "Alice", 0);
        var bob = registration(new UniqueId(2L), new UniqueId(12L), "Bob", 1);
        when(registrationService.list(EVENT)).thenReturn(List.of(alice, bob));
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var dialog = open();
        buttonsByAriaLabel(dialog, "events.participants.move-down").get(0).click();

        verify(registrationService).moveDown(new UniqueId(1L));
    }

    @Test
    void unregister_callsTheService() {
        var alice = registration(new UniqueId(1L), new UniqueId(11L), "Alice", 0);
        when(registrationService.list(EVENT)).thenReturn(List.of(alice));
        when(participantService.browse(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var dialog = open();
        buttonsByAriaLabel(dialog, "events.participants.unregister").get(0).click();

        verify(registrationService).unregister(new UniqueId(1L));
    }

    private static WorkspaceEventRegistrationModel registration(
            UniqueId id, UniqueId participantId, String label, int orderBy) {
        return WorkspaceEventRegistrationModel.builder()
                .uniqueId(id).eventUniqueId(EVENT).participantUniqueId(participantId)
                .participantLabel(label).orderBy(orderBy).build();
    }

    private static WorkspaceParticipantModel participant(UniqueId id, String label) {
        return WorkspaceParticipantModel.builder().uniqueId(id).label(label).build();
    }

    private static List<Button> buttonsByAriaLabel(EventParticipantsDialog dialog, String ariaLabel) {
        return descendants(dialog.registeredSectionForTest())
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> ariaLabel.equals(button.getAriaLabel().orElse(null))).toList();
    }

    private static Stream<Component> descendants(Component component) {
        return component.getChildren().flatMap(child ->
                Stream.concat(Stream.of(child), descendants(child)));
    }
}
