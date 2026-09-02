package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.WorkspaceModel;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.Permissions;
import vg.rg.service.WorkspaceLimitReachedException;
import vg.rg.service.WorkspaceNotRemovableException;
import vg.rg.service.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The workspace list and its lifecycle actions. What matters most here is the safety around removal: it
 * is confirmed first, it cannot be double-submitted, and it is not even offered for the default
 * workspace — which is what guarantees the user always has one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspacesViewTest {

    private static final UniqueId DEFAULT_ID = new UniqueId(5001L);
    private static final UniqueId ORDINARY_ID = new UniqueId(5002L);

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceService workspaceService;
    @Mock BeforeEnterEvent event;

    /**
     * A dialog auto-adds itself to the current UI when opened, so these flows need one.
     *
     * <p>Held in a field on purpose: Vaadin's {@code CurrentInstance} keeps the current UI behind a weak
     * reference, so a UI with no other referent can be collected mid-test and {@code dialog.open()} then
     * fails with "No currently active UI found" at random.
     */
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
    void list_showsTheDefaultBadgeAndTheLocalizedLabel() {
        var view = entered(systemNamedDefault(), ordinary("Field work"));

        // The system-created default stores no name, so its label comes from the bundle.
        assertThat(texts(view, Span.class))
                .contains("workspace.default.name", "workspace.default.badge", "Field work");
    }

    @Test
    void defaultWorkspace_isNotOfferedForRemoval() {
        // Not offered rather than offered-and-refused: the affordance would be a dead end.
        var view = entered(systemNamedDefault());

        assertThat(texts(view, Button.class)).doesNotContain("workspaces.remove");
        assertThat(texts(view, Button.class)).contains("workspaces.rename");
    }

    @Test
    void ordinaryWorkspace_isOfferedForRemoval() {
        var view = entered(ordinary("Field work"));

        assertThat(texts(view, Button.class)).contains("workspaces.remove", "workspaces.rename");
    }

    @Test
    void removal_requiresConfirmation_andSaysWhatItTakesWithIt() {
        var view = entered(ordinary("Field work"));

        var prompt = view.confirmRemove(ordinary("Field work"));

        assertThat(prompt.dialog().isOpened()).isTrue();
        assertThat(texts(prompt.dialog(), Paragraph.class)).contains("workspaces.remove.confirm");
        // Opening the dialog removes nothing.
        verify(workspaceService, never()).delete(any());
    }

    @Test
    void removal_confirmed_deletesOnceAndClosesTheDialog() {
        var view = entered(ordinary("Field work"));
        var prompt = view.confirmRemove(ordinary("Field work"));

        click(prompt.confirm());

        verify(workspaceService, times(1)).delete(ORDINARY_ID);
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    @Test
    void removal_doubleTap_deletesOnlyOnce() {
        // The confirm button disables itself on the first press, so a second tap cannot remove twice.
        var view = entered(ordinary("Field work"));
        var prompt = view.confirmRemove(ordinary("Field work"));
        var confirm = prompt.confirm();

        click(confirm);
        assertThat(confirm.isEnabled()).isFalse();
        click(confirm);

        verify(workspaceService, times(1)).delete(ORDINARY_ID);
    }

    @Test
    void removal_cancelled_removesNothing() {
        var view = entered(ordinary("Field work"));
        var prompt = view.confirmRemove(ordinary("Field work"));

        click(prompt.cancel());

        verify(workspaceService, never()).delete(any());
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    @Test
    void removal_refusedByTheService_isReportedNotPropagated() {
        var view = entered(ordinary("Field work"));
        doThrow(new WorkspaceNotRemovableException()).when(workspaceService).delete(any());
        var prompt = view.confirmRemove(ordinary("Field work"));

        // A refusal must surface as a message, never as an exception escaping the click handler.
        assertThatCode(() -> click(prompt.confirm()))
                .doesNotThrowAnyException();
    }

    @Test
    void rename_systemNamedWorkspace_startsFromAnEmptyField() {
        // Pre-filling with the localized label would silently freeze a locale-following label into a
        // stored string in whatever language happened to be active.
        var view = entered(systemNamedDefault());

        var prompt = view.openRenameDialog(systemNamedDefault());

        assertThat(values(prompt.dialog())).containsOnly("");
    }

    @Test
    void rename_userNamedWorkspace_startsFromItsStoredName() {
        var view = entered(ordinary("Field work"));

        var prompt = view.openRenameDialog(ordinary("Field work"));

        assertThat(values(prompt.dialog())).contains("Field work");
    }

    @Test
    void rename_saved_updatesAndClosesTheDialog() {
        var view = entered(ordinary("Field work"));
        var prompt = view.openRenameDialog(ordinary("Field work"));

        click(prompt.confirm());

        verify(workspaceService).update(any());
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    @Test
    void rename_staleSave_keepsTheDialogOpen() {
        // The edit must survive a stale save so the user can reload and retry rather than retype it.
        var view = entered(ordinary("Field work"));
        when(workspaceService.update(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        WorkspaceModel.class, ORDINARY_ID));
        var prompt = view.openRenameDialog(ordinary("Field work"));

        assertThatCode(() -> click(prompt.confirm()))
                .doesNotThrowAnyException();

        assertThat(prompt.dialog().isOpened()).isTrue();
    }

    @Test
    void create_atTheLimit_reportsTheRefusalWithoutPropagating() {
        var view = entered(ordinary("Field work"));
        when(workspaceService.create(any())).thenThrow(new WorkspaceLimitReachedException(20));

        var create = descendants(view).stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "workspaces.create".equals(button.getText()))
                .findFirst().orElseThrow();

        assertThatCode(() -> click(create)).doesNotThrowAnyException();
        verify(workspaceService).create(any());
    }

    // ------------------------------------------------------------------------------------ fixtures

    private WorkspacesView entered(WorkspaceModel... owned) {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(workspaceService.listOwned()).thenReturn(List.of(owned));

        var view = new WorkspacesView(localization, authorityChecker, workspaceService);
        view.beforeEnter(event);
        return view;
    }

    private static WorkspaceModel systemNamedDefault() {
        return WorkspaceModel.builder().uniqueId(DEFAULT_ID).name(null).defaultWorkspace(true).build();
    }

    private static WorkspaceModel ordinary(String name) {
        return WorkspaceModel.builder().uniqueId(ORDINARY_ID).name(name).build();
    }

    // ----------------------------------------------------------------------------------- traversal

    private static <T extends Component> List<String> texts(Component root, Class<T> type) {
        return descendants(root).stream()
                .filter(type::isInstance)
                .map(component -> component instanceof Button button ? button.getText()
                        : component.getElement().getText())
                .toList();
    }

    private static List<String> values(Dialog dialog) {
        return descendants(dialog).stream()
                .filter(HasValue.class::isInstance)
                .map(component -> String.valueOf(((HasValue<?, ?>) component).getValue()))
                .toList();
    }

    private static void click(Button button) {
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
    }

    private static List<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
                .toList();
    }
}
