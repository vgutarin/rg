package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.WorkspaceModel;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.Permissions;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.service.WorkspaceLimitReachedException;
import vg.rg.service.WorkspaceNotRemovableException;
import vg.rg.service.WorkspaceService;


/**
 * Lists the workspaces a user owns and lets them create another. Mobile-first: a single column, a
 * full-width form, and explicit empty and error states.
 *
 * <p>Removal is the most destructive action in the feature, so it is confirmed first, the confirmation
 * says plainly that the contained objects go too, and the confirm button is disabled the moment it is
 * pressed — a double tap must not delete twice.
 */
@Slf4j
@PageTitle("page.workspaces.title")
@Route(value = "workspaces", layout = WorkspaceLayout.class)
@PermitAll
public class WorkspacesView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final LocalizationService localization;
    private final AuthorityChecker authorityChecker;
    private final transient WorkspaceService workspaceService;

    private final Div content = new Div();

    public WorkspacesView(LocalizationService localization,
                          AuthorityChecker authorityChecker,
                          WorkspaceService workspaceService) {
        this.localization = localization;
        this.authorityChecker = authorityChecker;
        this.workspaceService = workspaceService;

        addClassName("secure-view");
        content.setWidthFull();
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // The layout already gates the section; this keeps the view safe if it is ever routed directly.
        if (!authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            event.rerouteTo(NoAccessView.class);
            return;
        }
        render();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        if (authorityChecker.hasAuthority(Permissions.Workspace.OWNER)) {
            render();
        } else {
            content.removeAll();
        }
    }

    private void render() {
        content.removeAll();
        content.add(new H2(localization.i18n("page.workspaces.title")));
        content.add(workspaceList());
        content.add(createForm());
    }

    private Component workspaceList() {
        var list = new Div();
        list.addClassName("workspace-list");

        var owned = workspaceService.listOwned();
        if (owned.isEmpty()) {
            // Should not normally happen -- the default is provisioned on entry -- but an empty state is
            // still the right answer rather than a blank panel.
            list.add(new Paragraph(localization.i18n("workspaces.empty")));
            return list;
        }
        owned.forEach(workspace -> list.add(row(workspace)));
        return list;
    }

    private Component row(WorkspaceModel workspace) {
        var row = new Div();
        row.addClassName("workspace-row");

        var name = new Span(label(workspace));
        name.addClassName("workspace-row-name");
        row.add(name);

        if (workspace.isDefaultWorkspace()) {
            var badge = new Span(localization.i18n("workspace.default.badge"));
            badge.addClassName("workspace-default-badge");
            row.add(badge);
        }
        if (workspace.getDescription() != null) {
            row.add(new Paragraph(workspace.getDescription()));
        }
        row.add(rowActions(workspace));
        return row;
    }

    private Component rowActions(WorkspaceModel workspace) {
        var actions = new Div();
        actions.addClassName("workspace-row-actions");

        var rename = new Button(localization.i18n("workspaces.rename"), VaadinIcon.EDIT.create(),
                event -> openRenameDialog(workspace));
        rename.addThemeVariants(ButtonVariant.TERTIARY);
        actions.add(rename);

        // The default workspace cannot be removed -- it is what guarantees the user always has one --
        // so the action is not offered rather than offered and refused.
        if (!workspace.isDefaultWorkspace()) {
            var remove = new Button(localization.i18n("workspaces.remove"), VaadinIcon.TRASH.create(),
                    event -> confirmRemove(workspace));
            remove.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.ERROR);
            actions.add(remove);
        }
        return actions;
    }

    /**
     * Rename, which is also how a system-named workspace acquires a stored name.
     *
     * @return the opened dialog and its buttons, so the flow can be driven directly
     */
    Prompt openRenameDialog(WorkspaceModel workspace) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("workspaces.rename"));

        var name = new TextField(localization.i18n("workspace.field.name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);
        // Empty for a system-named workspace: the localized label is not the user's text, so offering it
        // for editing would silently turn a locale-following label into a frozen string.
        name.setValue(workspace.getName() == null ? "" : workspace.getName());

        var description = new TextArea(localization.i18n("workspace.field.description"));
        description.setWidthFull();
        description.setHelperText(localization.i18n("workspace.field.personal-data-guidance"));
        description.setValue(workspace.getDescription() == null ? "" : workspace.getDescription());

        var save = new Button(localization.i18n("workspaces.save"), event -> {
            workspace.setName(name.getValue());
            workspace.setDescription(description.getValue());
            if (rename(workspace)) {
                dialog.close();
            }
        });
        var cancel = new Button(localization.i18n("workspaces.cancel"), event -> dialog.close());
        dialog.getFooter().add(cancel, save);
        dialog.add(name, description);
        dialog.open();
        return new Prompt(dialog, save, cancel);
    }

    private boolean rename(WorkspaceModel workspace) {
        try {
            workspaceService.update(workspace);
        } catch (ObjectOptimisticLockingFailureException stale) {
            // Someone else advanced the record; never silently overwrite their change. The dialog stays
            // open with the text still in it, so the user can reload and retry rather than retype.
            Notification.show(localization.i18n("workspaces.stale"));
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
        refresh();
        return true;
    }

    /**
     * Removal, confirmed first; see {@link Dialogs#confirmDeletion} for the shape and its guarantee.
     *
     * @return the opened confirmation dialog and its buttons, so the flow can be driven directly
     */
    Prompt confirmRemove(WorkspaceModel workspace) {
        return Dialogs.confirmDeletion(localization,
                "workspaces.remove", "workspaces.remove.confirm", "workspaces.remove",
                "workspaces.cancel", () -> remove(workspace));
    }

    private void remove(WorkspaceModel workspace) {
        try {
            workspaceService.delete(workspace.getUniqueId());
            Notification.show(localization.i18n("workspaces.removed"));
            refresh();
        } catch (WorkspaceNotRemovableException notRemovable) {
            Notification.show(localization.i18n(WorkspaceNotRemovableException.MESSAGE_KEY));
        } catch (RuntimeException failure) {
            Notification.show(localization.i18n(failure));
        }
    }

    /**
     * Re-renders in place, then asks the browser to reload so the surrounding selector picks the change
     * up too. Deliberately cannot fail the caller: the mutation has already committed by this point, so
     * a refresh problem is reported and swallowed rather than reversing the outcome.
     */
    private void refresh() {
        render();
        try {
            var ui = UI.getCurrent();
            if (ui != null) {
                ui.getPage().reload();
            }
        } catch (RuntimeException refreshFailed) {
            log.debug("Workspace list refresh failed after a successful change", refreshFailed);
        }
    }

    /**
     * A workspace the system created stores no name, because a stored name cannot follow the viewer's
     * locale; its label comes from a message key instead. A user-supplied name shows verbatim.
     */
    private String label(WorkspaceModel workspace) {
        return workspace.isSystemNamed()
                ? localization.i18n("workspace.default.name")
                : workspace.getName();
    }

    private Component createForm() {
        var form = new Div();
        form.addClassName("workspace-create-form");

        var name = new TextField(localization.i18n("workspace.field.name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);

        var description = new TextArea(localization.i18n("workspace.field.description"));
        description.setWidthFull();
        // The user's own content, stored as given. The guidance discourages other people's personal data
        // rather than scanning or blocking it.
        description.setHelperText(localization.i18n("workspace.field.personal-data-guidance"));

        var save = new Button(localization.i18n("workspaces.create"), event -> {
            save(name.getValue(), description.getValue());
        });
        save.setWidthFull();

        form.add(new H2(localization.i18n("workspaces.create.title")), name, description, save);
        return form;
    }

    private void save(String name, String description) {
        try {
            workspaceService.create(WorkspaceModel.builder()
                    .name(name)
                    .description(description)
                    .build());
            // Re-navigate so the selector in the surrounding layout picks up the new workspace too.
            UI.getCurrent().getPage().reload();
        } catch (WorkspaceLimitReachedException limitReached) {
            Notification.show(localization.i18n(WorkspaceLimitReachedException.MESSAGE_KEY));
        } catch (IllegalArgumentException invalid) {
            // The business layer returns a stable message key; translation belongs here.
            Notification.show(localization.i18n(invalid.getMessage()));
        }
    }
}
