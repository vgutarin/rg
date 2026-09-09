package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.RouterLayout;
import jakarta.annotation.security.PermitAll;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.MainView;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.rg.service.workspace.WorkspaceService;


/**
 * The workspace navigation section, and the <strong>only</strong> component that renders the
 * active-workspace selector.
 *
 * <p>Confining the selector here is structural rather than a rule each view must remember: because no
 * other layout contains it, it cannot appear outside the section. Leaving removes it; returning restores
 * it with the same workspace still active.
 *
 * <p>Entry is gated on the application-wide workspace permission, and that is the <em>only</em> gate.
 * Adding a capability gate here would be wrong: owning a workspace grants complete authority over its
 * contents, so a user could be shown less than they can actually use.
 */
@ParentLayout(MainView.class)
@PermitAll
public class WorkspaceLayout extends VerticalLayout
        implements RouterLayout, BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient WorkspaceSelectionService selectionService;
    private final transient WorkspaceService workspaceService;

    private final Div header = new Div();
    private final Div childContent = new Div();
    private final Select<WorkspaceModel> selector = new Select<>();

    /** Set on entry so the child views can scope their queries without resolving anything themselves. */
    private WorkspaceModel activeWorkspace;

    public WorkspaceLayout(LocalizationService localization,
                           AuthorityChecker authorityChecker,
                           WorkspaceSelectionService selectionService,
                           WorkspaceService workspaceService) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.selectionService = selectionService;
        this.workspaceService = workspaceService;

        addClassName("workspace-section");
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        header.addClassName("workspace-header");
        childContent.addClassName("workspace-content");
        childContent.setWidthFull();

        selector.addClassName("workspace-selector");
        selector.setWidthFull();
        selector.setItemLabelGenerator(this::workspaceLabel);
        selector.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                selectWorkspace(event.getValue());
            }
        });

        add(header, childContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            // Navigation visibility is presentation only; this is the enforcement, so reaching the route
            // directly is denied identically. NoAccessView discloses nothing about the section.
            event.rerouteTo(NoAccessView.class);
            return;
        }
        // Resolves once per navigation, provisioning the default on first entry and repairing a stale
        // pointer, so a removed or disowned workspace can never produce an error screen.
        activeWorkspace = selectionService.activeWorkspace();
        renderHeader();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (activeWorkspace != null) {
            renderHeader();
        }
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        childContent.removeAll();
        if (content != null) {
            childContent.getElement().appendChild(content.getElement());
        }
    }

    /** The workspace every child view scopes its queries to. */
    public WorkspaceModel activeWorkspace() {
        return activeWorkspace;
    }

    private void renderHeader() {
        header.removeAll();
        selector.setLabel(localization.i18n("workspace.selector.label"));

        var owned = workspaceService.listOwned();
        selector.setItems(owned);
        // Match by identifier rather than by instance: listOwned returns freshly mapped models, so the
        // active one is an equal-but-different object.
        owned.stream()
                .filter(workspace -> workspace.getUniqueId().equals(activeWorkspace.getUniqueId()))
                .findFirst()
                .ifPresentOrElse(selector::setValue, () -> selector.setValue(activeWorkspace));

        // A single owned workspace needs no switcher.
        selector.setVisible(owned.size() > 1);
        header.add(selector);
        // The default workspace is the implicit place to be, so naming it says nothing the user did not
        // already assume -- it is only worth stating once they have chosen to work somewhere else.
        if (!activeWorkspace.isDefaultWorkspace()) {
            header.add(activeWorkspaceCaption());
        }
    }

    /**
     * Names the active workspace, for every workspace except the default. Concurrent selections from two
     * sessions resolve last-write-wins on one row, which is exactly why a non-default choice is stated
     * rather than assumed.
     */
    private Component activeWorkspaceCaption() {
        var caption = new Paragraph(
                localization.i18n("workspace.active") + " " + workspaceLabel(activeWorkspace));
        caption.addClassName("workspace-active-caption");
        return caption;
    }

    /**
     * A workspace the <em>system</em> created stores no name, because a stored name cannot follow the
     * viewer's locale. Render its label from a message key; a user-supplied name is shown verbatim.
     */
    private String workspaceLabel(WorkspaceModel workspace) {
        if (workspace == null) {
            return "";
        }
        return workspace.isSystemNamed()
                ? localization.i18n("workspace.default.name")
                : workspace.getName();
    }

    /**
     * Switching workspace is one user action: the selection is stored, the active workspace is
     * re-resolved, and the child view re-renders wholly against it with nothing carried over.
     */
    void selectWorkspace(WorkspaceModel workspace) {
        selectionService.select(workspace.getUniqueId());
        // Re-resolve rather than trusting the picked model. The selection service is the authority, and
        // re-reading also repairs the pointer if the chosen workspace disappeared between render and
        // click -- in which case the user lands on their default rather than on an error.
        activeWorkspace = selectionService.activeWorkspace();
        renderHeader();
        refreshChildContent();
    }

    /**
     * Re-runs the child view's {@code beforeEnter} so it re-queries against the newly active workspace.
     * Null-safe on the UI: the state change above is what matters and must remain testable without one.
     */
    private void refreshChildContent() {
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().reload();
        }
    }
}
