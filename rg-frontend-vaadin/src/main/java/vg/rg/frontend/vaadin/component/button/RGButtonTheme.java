package vg.rg.frontend.vaadin.component.button;

/**
 * Theme-name tokens this application adds to {@code vaadin-button} to opt into shared styling defined in
 * {@code styles.css}.
 *
 * <p>Constants rather than string literals because a token here is a <strong>contract</strong> between
 * the stylesheet and every call site that tags a button: the two must spell it identically, and a typo in
 * either fails silently — a rule that never matches, a button that never gets the affordance. This is the
 * same reasoning that keeps {@code DisclosureList}'s CSS class names as constants.
 */
public final class RGButtonTheme {

    /**
     * Marks a button as a bordered action, so it reads as a pressable control. Styling is opt-in: a
     * button carries a border only when tagged with this, applied via
     * {@code button.addThemeName(RGButtonTheme.BORDERED)}. Colour still comes from the button's intent
     * variant (accent for ordinary actions, error red for a destructive one) through {@code currentColor};
     * this token governs only the border. Icon-only buttons are simply left untagged.
     */
    public static final String BORDERED = "rg-bordered";

    private RGButtonTheme() {
    }
}
