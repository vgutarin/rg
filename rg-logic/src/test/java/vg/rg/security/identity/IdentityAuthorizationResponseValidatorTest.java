package vg.rg.security.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class IdentityAuthorizationResponseValidatorTest {

    @Test
    void validate_absentConfiguration_usesDefaults() {
        var limits = limits();

        assertThat(limits.maxPermissionCount()).isEqualTo(1024);
        assertThat(limits.maxPermissionLength()).isEqualTo(128);
    }

    @Test
    void validate_defaultLimits_acceptExactBoundariesAndRejectOneOver() {
        var validator = new IdentityAuthorizationResponseValidator(limits());
        var exactCount = IntStream.range(0, 1024)
                .mapToObj(index -> "resource" + index + ":view")
                .toList();

        assertThat(validator.validate(exactCount)).isPresent();
        assertThat(validator.validate(IntStream.range(0, 1025)
                .mapToObj(index -> "resource" + index + ":view")
                .toList())).isEmpty();
        assertThat(validator.validate(List.of("a".repeat(123) + ":view"))).isPresent();
        assertThat(validator.validate(List.of("a".repeat(124) + ":view"))).isEmpty();
    }

    @Test
    void validate_customLimits_acceptsExactUniqueCountAndLength() {
        var validator = validator(2, 10);

        assertThat(validator.validate(List.of("alpha:view", "beta:view")))
                .hasValueSatisfying(permissions -> assertThat(permissions)
                        .containsExactlyInAnyOrder("alpha:view", "beta:view"));
    }

    @Test
    void validate_uniqueCountOneOverLimit_returnsIncompatibleSignal() {
        assertThat(validator(1, 128).validate(List.of("alpha:view", "beta:view"))).isEmpty();
    }

    @Test
    void validate_lengthOneOverLimit_returnsIncompatibleSignal() {
        assertThat(validator(10, 9).validate(List.of("alpha:view"))).isEmpty();
    }

    @Test
    void validate_duplicates_deduplicatesBeforeCountAndWarnsOnceWithoutValues(CapturedOutput output) {
        var result = validator(1, 128).validate(List.of("alpha:view", "alpha:view", "alpha:view"));

        assertThat(result).contains(java.util.Set.of("alpha:view"));
        assertThat(output.getAll())
                .containsOnlyOnce("Identity authorization returned duplicate permissions")
                .doesNotContain("alpha:view");
    }

    @Test
    void validate_malformedPermission_returnsIncompatibleSignal() {
        assertThat(validator(10, 128).validate(List.of("malformed"))).isEmpty();
    }

    @Test
    void validate_nullPermissions_returnsIncompatibleSignal() {
        assertThat(validator(10, 128).validate(null)).isEmpty();
    }

    @Test
    void limits_zeroNegativeAndMalformedValues_failWithoutDisclosure() {
        assertInvalid(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_COUNT_PROPERTY, "0");
        assertInvalid(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_LENGTH_PROPERTY, "-1");
        assertInvalid(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_COUNT_PROPERTY,
                "sensitive-malformed-marker");
    }

    private IdentityAuthorizationResponseValidator validator(int count, int length) {
        var environment = new MockEnvironment()
                .withProperty(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_COUNT_PROPERTY,
                        Integer.toString(count))
                .withProperty(IdentityAuthorizationLimitsProperties.MAX_PERMISSION_LENGTH_PROPERTY,
                        Integer.toString(length));
        return new IdentityAuthorizationResponseValidator(new IdentityAuthorizationLimitsProperties(environment));
    }

    private IdentityAuthorizationLimitsProperties limits() {
        return new IdentityAuthorizationLimitsProperties(new MockEnvironment());
    }

    private void assertInvalid(String property, String value) {
        var environment = new MockEnvironment().withProperty(property, value);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new IdentityAuthorizationLimitsProperties(environment)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid configuration for " + property)
                .hasMessageNotContaining(value);
    }
}
