package vg.rg.model.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantPhoneTest {

    @Test
    void normalize_absentValue_isNull() {
        // A participant may be recorded with a label alone.
        assertThat(ParticipantPhone.normalize(null)).isNull();
        assertThat(ParticipantPhone.normalize("   ")).isNull();
    }

    @Test
    void normalize_stripsFormattingAndKeepsTheCountryCode() {
        assertThat(ParticipantPhone.normalize("+380 (50) 111-22.33")).isEqualTo("+380501112233");
        assertThat(ParticipantPhone.normalize("  050 111 2233 ")).isEqualTo("0501112233");
    }

    /** A number pasted from a web page or a chat message commonly carries non-breaking spaces. */
    @Test
    void normalize_toleratesNonBreakingSpaces() {
        assertThat(ParticipantPhone.normalize("+380 50 111 2233"))
                .isEqualTo("+380501112233");
    }

    /**
     * Different spellings of one number must collapse to one value, or duplicate detection and the
     * reveal action would disagree about whether two participants are the same person.
     */
    @Test
    void normalize_isCanonicalAcrossSpellings() {
        assertThat(ParticipantPhone.normalize("+380501112233"))
                .isEqualTo(ParticipantPhone.normalize("+380 50 111 22 33"))
                .isEqualTo(ParticipantPhone.normalize("+380-50-111-22-33"));
    }

    /**
     * No region is inferred, so a national number and its international form stay distinct. Recorded as
     * a test because it is a deliberate decision rather than an oversight: guessing a country code from
     * the viewer's locale would attach a meaning the owner never typed.
     */
    @Test
    void normalize_doesNotInferACountryCode() {
        assertThat(ParticipantPhone.normalize("0501112233"))
                .isNotEqualTo(ParticipantPhone.normalize("+380501112233"));
    }

    /**
     * The message is a key, and must stay one: prose would be untranslatable, and prose containing the
     * value would put a phone number wherever the exception is logged.
     */
    @Test
    void normalize_rejectsImplausibleValuesWithoutEchoingThem() {
        assertThatThrownBy(() -> ParticipantPhone.normalize("+38050111223344556677"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ParticipantPhone.INVALID_MESSAGE_KEY);
        assertThatThrownBy(() -> ParticipantPhone.normalize("1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("1234");
        assertThatThrownBy(() -> ParticipantPhone.normalize("call me maybe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ParticipantPhone.INVALID_MESSAGE_KEY);
    }

    @Test
    void aFullyFormattedNumber_fitsWithinTheInputBound() {
        var formatted = "+380 (50) 111-22-33";

        assertThat(formatted.length())
                .isGreaterThan(ParticipantPhone.MAX_LENGTH)
                .isLessThanOrEqualTo(ParticipantPhone.MAX_INPUT_LENGTH);
        assertThat(ParticipantPhone.normalize(formatted).length())
                .isLessThanOrEqualTo(ParticipantPhone.MAX_LENGTH);
    }

}
