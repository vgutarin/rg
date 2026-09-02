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
                Permissions.Reports.READ,
                Permissions.Request.SUBMIT,
                Permissions.Location.READ,
                Permissions.Location.CREATE,
                Permissions.Location.UPDATE,
                Permissions.Location.DELETE);
    }

    @Test
    void all_locationPermissions_areRecognizedAndWellFormed() {
        var locationPermissions = List.of(
                Permissions.Location.READ,
                Permissions.Location.CREATE,
                Permissions.Location.UPDATE,
                Permissions.Location.DELETE);

        assertThat(locationPermissions).allSatisfy(permission -> {
            assertThat(Permissions.hasValidFormat(permission)).isTrue();
            assertThat(Permissions.isRecognized(permission)).isTrue();
            assertThat(Permissions.ALL).contains(permission);
        });
    }

    @Test
    void all_mutationAttempt_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> Permissions.ALL.add("other:view"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasValidFormat_validPermission_returnsTrue() {
        assertThat(Permissions.hasValidFormat("home:read")).isTrue();
    }

    @Test
    void hasValidFormat_uppercasePermission_returnsFalse() {
        assertThat(Permissions.hasValidFormat("Home:View")).isFalse();
    }

    @Test
    void validateAndFreeze_duplicatePermission_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permissions.validateAndFreeze(List.of("home:read", "home:read")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate permission declaration: home:read");
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
        assertThat(
                Permissions.recognized(
                        Set.of("unknown:view", Permissions.Request.SUBMIT)
                )
        ).containsExactly(Permissions.Request.SUBMIT);
    }
}
