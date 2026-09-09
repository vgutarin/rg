package vg.rg.model.workspace;

/**
 * Normalization for a participant's contact number.
 *
 * <p>Normalizing matters for two reasons that pull in the same direction: the stored value is encrypted,
 * so the database can neither compare nor de-duplicate it, and the reveal action hands back whatever was
 * stored. Without one canonical form, duplicate detection and display would disagree with each other —
 * {@code +380 50 111 2233} and {@code +380501112233} would be two different participants.
 *
 * <p><strong>No region is inferred.</strong> A number without a country code is kept as the digits given,
 * because guessing one from the viewer's locale would silently attach a meaning the owner did not type.
 * That mirrors the constitution's rule that currency and timezone semantics stay explicit rather than
 * being derived from locale. The consequence is that two spellings of one number can still be recorded
 * twice when only one carries a country code — visible to the owner, and preferable to a wrong guess.
 */
public final class ParticipantPhone {

    /** Enough for E.164's fifteen digits plus a leading {@code +}. The length of a stored value. */
    public static final int MAX_LENGTH = 16;

    /**
     * What an input field should allow, which is more than {@link #MAX_LENGTH}: people type spaces,
     * dashes and parentheses, and {@link #normalize(String)} strips them. Capping input at the stored
     * length would reject a perfectly valid number mid-typing.
     */
    public static final int MAX_INPUT_LENGTH = 32;

    /**
     * Stable outcome code for an implausible number; the UI owns its translation.
     *
     * <p>A key rather than prose, and deliberately so: the constitution requires domain layers to expose
     * non-localized outcome codes, and a message that echoed the offending value would put a phone
     * number into whatever logged the exception.
     */
    public static final String INVALID_MESSAGE_KEY = "workspace.participant.error.phone-invalid";

    private static final int MIN_DIGITS = 5;
    private static final int MAX_DIGITS = 15;
    /**
     * Strips formatting and validates what remains.
     *
     * @return the canonical form — an optional leading {@code +} followed by digits — or {@code null}
     *         when nothing was supplied, since a participant may be recorded with a label alone
     * @throws IllegalArgumentException carrying {@link #INVALID_MESSAGE_KEY} if a value is present but
     *                                  is not a plausible phone number. Never echoes the value.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var trimmed = raw.trim();
        var international = trimmed.startsWith("+");
        var digits = new StringBuilder(trimmed.length());
        for (var index = international ? 1 : 0; index < trimmed.length(); index++) {
            var character = trimmed.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (!isSeparator(character)) {
                throw new IllegalArgumentException(INVALID_MESSAGE_KEY);
            }
        }
        if (digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) {
            throw new IllegalArgumentException(INVALID_MESSAGE_KEY);
        }
        return international ? "+" + digits : digits.toString();
    }

    private static boolean isSeparator(char character) {
        return character == ' '
                // A number pasted from a web page or a chat often carries a non-breaking space.
                || character == '\u00a0'
                || character == '-'
                || character == '.'
                || character == '('
                || character == ')';
    }

    private ParticipantPhone() {
    }
}
