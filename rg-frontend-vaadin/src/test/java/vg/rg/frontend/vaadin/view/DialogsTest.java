package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The confirmation's contract, tested once rather than three times through its callers.
 *
 * <p>The three screens that delete something each have their own test that the flow is wired up, but
 * the guarantee itself belongs here — it was absent from one of those screens for as long as the screen
 * existed, precisely because it lived in three copies with nothing pinning it in one place.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DialogsTest {

    @Mock LocalizationService localization;

    /** A dialog auto-adds itself to the current UI when opened; held in a field so it is not collected. */
    private UI ui;

    @BeforeEach
    void attachUi() {
        ui = new UI();
        UI.setCurrent(ui);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    @Test
    void opensWithTheGivenLabels_andRunsNothingUntilConfirmed() {
        var runs = new AtomicInteger();

        var prompt = confirmation(runs);

        assertThat(prompt.dialog().isOpened()).isTrue();
        assertThat(prompt.confirm().getText()).isEqualTo("confirm.key");
        assertThat(prompt.cancel().getText()).isEqualTo("cancel.key");
        assertThat(runs).hasValue(0);
    }

    /**
     * The guarantee this helper exists for. A destructive action must not run twice because of a double
     * tap, a slow round trip, or a repeated request — and disabling the button is presentation, not
     * enforcement, so a second event can still arrive.
     */
    @Test
    void aDoublePress_runsTheActionOnce() {
        var runs = new AtomicInteger();
        var prompt = confirmation(runs);

        click(prompt.confirm());
        click(prompt.confirm());

        assertThat(runs).hasValue(1);
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    @Test
    void cancelling_runsNothingAndCloses() {
        var runs = new AtomicInteger();
        var prompt = confirmation(runs);

        click(prompt.cancel());

        assertThat(runs).hasValue(0);
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    /** The destructive action has to look destructive, on every screen that uses this. */
    @Test
    void theConfirmButtonIsStyledAsTheDestructiveAction() {
        assertThat(confirmation(new AtomicInteger()).confirm().getThemeNames())
                .contains("primary", "error");
    }

    /**
     * The dialog closes even when the action fails. Leaving it open would strand the user in a dialog
     * whose only action is already disabled, so a failing action reports itself instead.
     */
    @Test
    void aFailingAction_stillClosesTheDialog() {
        var prompt = Dialogs.confirmDeletion(localization, "t", "m", "c", "x", () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(catchThrowable(() -> click(prompt.confirm()))).isNotNull();
        assertThat(prompt.dialog().isOpened()).isFalse();
    }

    private Prompt confirmation(AtomicInteger runs) {
        return Dialogs.confirmDeletion(localization,
                "title.key", "message.key", "confirm.key", "cancel.key", runs::incrementAndGet);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static void click(Component element) {
        ComponentUtil.fireEvent(element, new ClickEvent<>(element));
    }
}
