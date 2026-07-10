package vg.rg.security;

import org.junit.jupiter.api.Test;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.Permissions;
import vg.rg.security.model.TelegramInitDataRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureAuthorizationContractModelTest {

    @Test
    void serialization_validAuthenticatedPrincipal_restoresEquivalentPrincipal() throws Exception {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1", "Test User", Set.of(Permissions.Home.VIEW), true,
                AuthenticationFlow.TELEGRAM);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(principal);
        }

        AuthenticatedUserPrincipal restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AuthenticatedUserPrincipal) input.readObject();
        }

        assertThat(restored).isEqualTo(principal);
    }

    @Test
    void serialization_provisionalPrincipal_preservesNullSubjectAndFalseConsent() throws Exception {
        var principal = new AuthenticatedUserPrincipal(
                null, null, Set.of(Permissions.Home.VIEW), false,
                AuthenticationFlow.TELEGRAM);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(principal);
        }

        AuthenticatedUserPrincipal restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AuthenticatedUserPrincipal) input.readObject();
        }

        assertThat(restored).isEqualTo(principal);
        assertThat(restored.sub()).isNull();
        assertThat(restored.consentGiven()).isFalse();
    }

    @Test
    void constructor_validTelegramInitData_preservesInitData() {
        var request = new TelegramInitDataRequest("auth_date=1&hash=x");

        assertThat(request.initData()).isEqualTo("auth_date=1&hash=x");
    }

    @Test
    void constructor_nullTelegramInitData_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TelegramInitDataRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_largeTelegramInitData_preservesValueForConfiguredBoundaryValidation() {
        var value = "x".repeat(32 * 1024 + 1);

        assertThat(new TelegramInitDataRequest(value).initData()).isEqualTo(value);
    }

    @Test
    void constructor_blankTelegramInitData_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new TelegramInitDataRequest("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_unknownPermission_preservesPermission() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1", "Test User", Set.of("home:view", "unknown:view"), true,
                AuthenticationFlow.TELEGRAM);

        assertThat(principal.permissions()).containsExactlyInAnyOrder(
                Permissions.Home.VIEW, "unknown:view");
    }

    @Test
    void constructor_nullSubject_preservesNullSubject() {
        var principal = new AuthenticatedUserPrincipal(
                null, "Test User", Set.of(), true, AuthenticationFlow.TELEGRAM);

        assertThat(principal.sub()).isNull();
    }

    @Test
    void constructor_emptyNonNullSubject_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AuthenticatedUserPrincipal(
                "", "Test User", Set.of(), true, AuthenticationFlow.TELEGRAM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_consentNotGiven_preservesFalseConsent() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1", "Test User", Set.of(), false, AuthenticationFlow.TELEGRAM);

        assertThat(principal.consentGiven()).isFalse();
    }

    @Test
    void constructor_malformedPermission_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AuthenticatedUserPrincipal(
                "subject-1", "Test User", Set.of("malformed"), true,
                AuthenticationFlow.TELEGRAM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_moreThan1024SyntacticallyValidPermissions_preservesValues() {
        var permissions = IntStream.range(0, 1025)
                .mapToObj(index -> "resource" + index + ":view")
                .collect(Collectors.toSet());

        var principal = new AuthenticatedUserPrincipal(
                "subject-1", null, permissions, true, AuthenticationFlow.TELEGRAM);

        assertThat(principal.permissions()).hasSize(1025).containsAll(permissions);
    }

    @Test
    void constructor_permissionLongerThan128Characters_preservesValue() {
        var permission = "r".repeat(129) + ":view";

        var principal = new AuthenticatedUserPrincipal(
                "subject-1", null, Set.of(permission), true, AuthenticationFlow.TELEGRAM);

        assertThat(principal.permissions()).containsExactly(permission);
    }

    @Test
    void constructor_permissionsAreImmutable() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1", null, Set.of(Permissions.Home.VIEW), true,
                AuthenticationFlow.TELEGRAM);

        assertThatThrownBy(() -> principal.permissions().add(Permissions.Reports.VIEW))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contractModels_haveNoRuntimeContractVersionField() {
        assertThat(Arrays.stream(TelegramInitDataRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain("contractVersion");
        assertThat(Arrays.stream(AuthorizationOutcome.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain("contractVersion");
    }

    @Test
    void authorized_validPrincipal_containsPrincipal() {
        var principal = new AuthenticatedUserPrincipal(
                "subject-1", "Test User", Set.of("home:view"), true,
                AuthenticationFlow.TELEGRAM);

        assertThat(AuthorizationOutcome.authorized(principal).principal()).contains(principal);
    }

    @Test
    void denied_noPrincipal_hasEmptyPrincipal() {
        assertThat(AuthorizationOutcome.denied().principal()).isEmpty();
    }
}
