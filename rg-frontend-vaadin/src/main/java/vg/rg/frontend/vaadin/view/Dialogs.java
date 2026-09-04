package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dialogs whose shape is the same on every screen that needs one.
 *
 * <p>Deliberately holds a <strong>single</strong> method, because there is currently a single such
 * shape. The form dialogs are not here: the workspace rename, the participant edit and
 * {@link LocationFormDialog} differ in fields, validation and error handling, so a common abstraction
 * over them would be parameter soup that each caller fights. Add a second method when a second shape
 * genuinely repeats — not in anticipation of one.
 */
final class Dialogs {

    /**
     * Confirms an irreversible removal, and guarantees it runs at most once.
     *
     * <p>Extracted because the three screens that delete something had this same twenty lines, and the
     * guard below — the part that actually matters — had silently gone missing from one of those
     * copies. Consolidating is what makes omitting it impossible rather than merely unlikely.
     *
     * <p>{@code action} runs on confirmation and the dialog closes afterwards <em>regardless of the
     * outcome</em>. Leaving it open on failure would strand the user in a dialog whose only action is
     * already disabled, so a failing action is expected to report itself — a notification, typically —
     * rather than rely on the dialog staying put.
     *
     * @return the opened dialog and its buttons, so a test can drive the flow; Vaadin footer components
     *         live in a virtual slot that cannot be reached from the dialog itself
     */
    static Prompt confirmDeletion(LocalizationService localization,
                                  String titleKey,
                                  String messageKey,
                                  String confirmKey,
                                  String cancelKey,
                                  Runnable action) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(titleKey));
        dialog.add(new Paragraph(localization.i18n(messageKey)));

        var confirm = new Button(localization.i18n(confirmKey));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        // Disabling the button is presentation; this flag is the enforcement. A destructive action must
        // not run twice because of a double tap, a slow round trip, or a repeated request.
        var submitted = new AtomicBoolean();
        confirm.addClickListener(event -> {
            if (!submitted.compareAndSet(false, true)) {
                return;
            }
            confirm.setEnabled(false);
            // finally, so a throwing action cannot strand the user in an open dialog whose only
            // control is already disabled. Callers are expected to report their own failures.
            try {
                action.run();
            } finally {
                dialog.close();
            }
        });
        var cancel = new Button(localization.i18n(cancelKey), event -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
        return new Prompt(dialog, confirm, cancel);
    }

    private Dialogs() {
    }
}
