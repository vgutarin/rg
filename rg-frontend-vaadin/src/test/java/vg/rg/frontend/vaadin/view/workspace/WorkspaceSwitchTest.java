package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.rg.service.workspace.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Switching the active workspace: one action stores the selection, re-resolves the active workspace, and
 * re-renders — with nothing of the previous workspace carried over.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceSwitchTest {

    private static final UniqueId ALPHA = new UniqueId(5001L);
    private static final UniqueId BETA = new UniqueId(5002L);

    @Mock
    LocalizationService localization;
    @Mock
    AuthorityChecker authorityChecker;
    @Mock
    WorkspaceSelectionService selectionService;
    @Mock
    WorkspaceService workspaceService;

    @Test
    void selecting_storesTheSelectionAndAdoptsTheNewActiveWorkspace() {
        var layout = entered();

        layout.selectWorkspace(named(BETA, "Beta"));

        assertThat(layout.activeWorkspace().getUniqueId()).isEqualTo(BETA);
    }

    @Test
    void selecting_storesBeforeReResolving() {
        // Order matters: re-resolving before storing would read the old selection and appear to do
        // nothing. The re-read is also what repairs the pointer if the chosen workspace vanished.
        var layout = entered();

        layout.selectWorkspace(named(BETA, "Beta"));

        InOrder order = inOrder(selectionService);
        order.verify(selectionService).select(BETA);
        order.verify(selectionService).activeWorkspace();
    }

    @Test
    void selecting_takesTheActiveWorkspaceFromTheServiceNotFromThePickedModel() {
        // The service is the authority. If the chosen workspace disappeared between render and click,
        // the user lands on whatever the service now reports rather than on a stale pick.
        var layout = entered();
        when(selectionService.activeWorkspace()).thenReturn(named(ALPHA, "Alpha"));

        layout.selectWorkspace(named(BETA, "Beta"));

        assertThat(layout.activeWorkspace().getUniqueId()).isEqualTo(ALPHA);
    }

    @Test
    void selecting_reRendersTheActiveWorkspaceCaption() {
        var layout = entered();

        layout.selectWorkspace(named(BETA, "Beta"));

        // The caption specifically, not the whole subtree: the selector legitimately still LISTS Alpha
        // as an option to switch back to. What must not survive is the statement of which is active.
        assertThat(captions(layout)).containsExactly("workspace.active Beta");
    }

    @Test
    void selecting_leavesExactlyOneActiveWorkspaceCaption() {
        // The header is rebuilt on every switch; a stale caption left beside the new one would show the
        // user two different answers to "which workspace am I in".
        var layout = entered();

        layout.selectWorkspace(named(BETA, "Beta"));
        layout.selectWorkspace(named(ALPHA, "Alpha"));

        assertThat(captions(layout)).hasSize(1);
    }

    @Test
    void switchingIsOneAction() {
        // A single call performs the whole switch: no confirm step, no second interaction.
        var layout = entered();

        layout.selectWorkspace(named(BETA, "Beta"));

        assertThat(layout.activeWorkspace().getUniqueId()).isEqualTo(BETA);
        assertThat(renderedText(layout)).contains("Beta");
    }

    private WorkspaceLayout entered() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(selectionService.activeWorkspace()).thenReturn(named(ALPHA, "Alpha"));
        when(workspaceService.listOwned())
                .thenReturn(List.of(named(ALPHA, "Alpha"), named(BETA, "Beta")));

        var layout = new WorkspaceLayout(
                localization, authorityChecker, selectionService, workspaceService);
        layout.beforeEnter(mock(BeforeEnterEvent.class));

        // After entry the switch target becomes what the service reports next.
        when(selectionService.activeWorkspace()).thenReturn(named(BETA, "Beta"));
        return layout;
    }

    private static WorkspaceModel named(UniqueId id, String name) {
        return WorkspaceModel.builder().uniqueId(id).name(name).build();
    }

    /** Text of every active-workspace caption in the layout. */
    private static List<String> captions(WorkspaceLayout layout) {
        var found = new java.util.ArrayList<String>();
        collectCaptions(layout.getElement(), found);
        return found;
    }

    private static void collectCaptions(com.vaadin.flow.dom.Element element, List<String> found) {
        if (element.getClassList().contains("workspace-active-caption")) {
            found.add(element.getText());
        }
        element.getChildren().forEach(child -> collectCaptions(child, found));
    }

    private static String renderedText(WorkspaceLayout layout) {
        var text = new StringBuilder();
        collect(layout.getElement(), text);
        return text.toString();
    }

    private static void collect(com.vaadin.flow.dom.Element element, StringBuilder text) {
        text.append(' ').append(element.getText() == null ? "" : element.getText());
        element.getPropertyNames()
                .filter(name -> name.equals("label") || name.equals("value"))
                .forEach(name -> text.append(' ').append(element.getProperty(name, "")));
        element.getChildren().forEach(child -> collect(child, text));
    }
}
