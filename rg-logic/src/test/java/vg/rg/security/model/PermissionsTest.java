package vg.rg.security.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionsTest {

    @Test
    void all_declaredPermissions_exposesStableCatalog() {
        assertThat(Permissions.ALL).containsExactly(
                Permissions.Home.VIEW,
                Permissions.Reports.VIEW,
                Permissions.Request.SUBMIT);
    }

    @Test
    void all_mutationAttempt_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> Permissions.ALL.add("other:view"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasValidFormat_validPermission_returnsTrue() {
        assertThat(Permissions.hasValidFormat("home:view")).isTrue();
    }

    @Test
    void hasValidFormat_uppercasePermission_returnsFalse() {
        assertThat(Permissions.hasValidFormat("Home:View")).isFalse();
    }

    @Test
    void validateAndFreeze_duplicatePermission_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permissions.validateAndFreeze(List.of("home:view", "home:view")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate permission declaration: home:view");
    }

    @Test
    void validateAndFreeze_uppercasePermission_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permissions.validateAndFreeze(List.of("Home:View")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid permission declaration: Home:View");
    }

    @Test
    void validateAndFreeze_invalidSeparator_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permissions.validateAndFreeze(List.of("home.view")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid permission declaration: home.view");
    }

    @Test
    void recognized_mixedPermissionSet_returnsRecognizedValuesInCatalogOrder() {
        assertThat(Permissions.recognized(Set.of(
                "unknown:view", Permissions.Request.SUBMIT, Permissions.Home.VIEW)))
                .containsExactly(Permissions.Home.VIEW, Permissions.Request.SUBMIT);
    }
}
