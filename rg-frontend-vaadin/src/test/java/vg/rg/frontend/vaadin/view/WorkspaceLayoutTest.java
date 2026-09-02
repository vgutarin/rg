package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.WorkspaceModel;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.rg.service.WorkspaceSelectionService;
import vg.rg.service.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The workspace section layout: how the active workspace is labelled, and that the section's gate is
 * enforced rather than merely reflected in navigation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceLayoutTest {

    private static final UniqueId WORKSPACE = new UniqueId(5001L);

    @Mock
    LocalizationService localization;
    @Mock
    AuthorityChecker authorityChecker;
    @Mock
    WorkspaceSelectionService selectionService;
    @Mock
    WorkspaceService workspaceService;

    @Test
    void systemNamedWorkspace_isLabelledFromAMessageKey() {
        // A system-created workspace stores no name, because a stored name cannot follow the viewer's
        // locale. Its label must therefore come from the bundle.
        var layout = enter(systemNamed());

        assertThat(layout.activeWorkspace().isSystemNamed()).isTrue();
        assertThat(renderedText(layout)).contains("workspace.default.name");
    }

    @Test
    void userNamedWorkspace_isLabelledVerbatim() {
        var layout = enter(named("Field work"));

        assertThat(layout.activeWorkspace().isSystemNamed()).isFalse();
        assertThat(renderedText(layout)).contains("Field work");
        // The localized default label must not leak in once the user has supplied a name.
        assertThat(renderedText(layout)).doesNotContain("workspace.default.name");
    }

    @Test
    void aNonDefaultActiveWorkspaceIsNamedOnEntry() {
        // Concurrent selections resolve last-write-wins on one row, which is why a deliberate choice of
        // workspace is stated rather than assumed.
        var layout = enter(named("Alpha"));

        assertThat(renderedText(layout)).contains("workspace.active", "Alpha");
        assertThat(captions(layout)).hasSize(1);
    }

    @Test
    void theDefaultActiveWorkspaceIsNotNamed() {
        // The default is the implicit place to be, so naming it tells the user nothing they had not
        // already assumed. It is only worth stating once they have chosen to work somewhere else.
        var layout = enter(defaultNamed("Alpha"));

        assertThat(captions(layout)).isEmpty();
        assertThat(renderedText(layout)).doesNotContain("workspace.active");
    }

    /** The active-workspace captions currently rendered, found by their class rather than by position. */
    private static java.util.List<String> captions(WorkspaceLayout layout) {
        var found = new java.util.ArrayList<String>();
        collectCaptions(layout.getElement(), found);
        return found;
    }

    private static void collectCaptions(com.vaadin.flow.dom.Element element,
                                        java.util.List<String> found) {
        if (element.getClassList().contains("workspace-active-caption")) {
            found.add(element.getText());
        }
        element.getChildren().forEach(child -> collectCaptions(child, found));
    }

    @Test
    void withoutTheWorkspacePermission_entryIsReroutedAndNothingIsProvisioned() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        var layout = newLayout();
        var event = mock(BeforeEnterEvent.class);

        layout.beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
        // Hiding the navigation entry is presentation; this is the enforcement. It must also not
        // provision a workspace for a user who is not entitled to one.
        verify(selectionService, never()).activeWorkspace();
        assertThat(layout.activeWorkspace()).isNull();
    }

    @Test
    void holdingOnlyLocalCapabilities_doesNotOpenTheSection() {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(false);
        when(authorityChecker.hasAuthority(LocalPermissions.Location.READ)).thenReturn(true);
        var layout = newLayout();
        var event = mock(BeforeEnterEvent.class);

        layout.beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    private WorkspaceLayout enter(WorkspaceModel active) {
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(selectionService.activeWorkspace()).thenReturn(active);
        when(workspaceService.listOwned()).thenReturn(List.of(active));

        var layout = newLayout();
        layout.beforeEnter(mock(BeforeEnterEvent.class));
        return layout;
    }

    private WorkspaceLayout newLayout() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return new WorkspaceLayout(localization, authorityChecker, selectionService, workspaceService);
    }

    /** Flattens the layout's rendered text so labels can be asserted without reaching into components. */
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

    private static WorkspaceModel systemNamed() {
        return WorkspaceModel.builder()
                .uniqueId(WORKSPACE)
                .name(null)
                .defaultWorkspace(true)
                .build();
    }

    private static WorkspaceModel named(String name) {
        return WorkspaceModel.builder().uniqueId(WORKSPACE).name(name).build();
    }

    /** A user-named workspace that also happens to be the owner's default. */
    private static WorkspaceModel defaultNamed(String name) {
        return WorkspaceModel.builder()
                .uniqueId(WORKSPACE)
                .name(name)
                .defaultWorkspace(true)
                .build();
    }
}
