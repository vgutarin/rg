package vg.rg.security.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionsTest {

    @Test
    void appWide_exposesStableCatalog() {
        assertThat(Permissions.APP_WIDE).containsExactly(
                Permissions.Reports.READ,
                Permissions.Request.SUBMIT,
                Permissions.Workspace.OWNER);
    }

    @Test
    void all_isAppWideOnly() {
        // Local permissions are deliberately absent: nothing checks whether they are held, so including
        // them here would imply an enforcement that does not exist -- and would leave two meanings of
        // "recognized" in this class.
        assertThat(Permissions.ALL).isEqualTo(Permissions.APP_WIDE);
        assertThat(Permissions.ALL).doesNotContain(
                LocalPermissions.Workspace.CREATE,
                LocalPermissions.Workspace.READ,
                LocalPermissions.Workspace.UPDATE,
                LocalPermissions.Workspace.DELETE);
    }

    @Test
    void workspaceOwner_isAppWideAndWellFormed() {
        assertThat(Permissions.hasValidFormat(Permissions.Workspace.OWNER)).isTrue();
        assertThat(Permissions.isRecognized(Permissions.Workspace.OWNER)).isTrue();
    }

    @Test
    void isRecognized_purelyLocalPermission_returnsFalse() {
        // A local permission is meaningless without a resource, so the flat check must reject it.
        assertThat(Permissions.isRecognized(LocalPermissions.Workspace.UPDATE)).isFalse();
        assertThat(Permissions.isRecognized(LocalPermissions.Workspace.DELETE)).isFalse();
        assertThat(Permissions.isRecognized(LocalPermissions.Workspace.READ)).isFalse();
        assertThat(Permissions.isRecognized(LocalPermissions.Workspace.CREATE)).isFalse();
    }

    @Test
    void isRecognized_everyLocalPermission_isRejectedByTheFlatCheck() {
        // The inversion the global-scope retirement was for. Location capabilities used to be accepted
        // here because the global screens guarded with the flat check; now nothing does, so a permission
        // that is meaningless without a resource can no longer be checked without one.
        assertThat(LocalPermissions.ALL).isNotEmpty();
        assertThat(LocalPermissions.ALL).allSatisfy(permission ->
                assertThat(Permissions.isRecognized(permission)).isFalse());
        assertThat(Permissions.APP_WIDE).doesNotContainAnyElementsOf(LocalPermissions.ALL);
    }

    @Test
    void isRecognized_unknownPermission_returnsFalse() {
        assertThat(Permissions.isRecognized("unknown:view")).isFalse();
        assertThat(Permissions.isRecognized(null)).isFalse();
    }

    @Test
    void appWide_mutationAttempt_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> Permissions.APP_WIDE.add("other:view"))
                .isInstanceOf(UnsupportedOperationException.class);
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
        assertThatThrownBy(() -> PermissionSyntax.validateAndFreeze(List.of("home:read", "home:read")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate permission declaration: home:read");
    }

    @Test
    void validateAndFreeze_uppercasePermission_throwsIllegalStateException() {
        assertThatThrownBy(() -> PermissionSyntax.validateAndFreeze(List.of("Home:View")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid permission declaration: Home:View");
    }

    @Test
    void validateAndFreeze_invalidSeparator_throwsIllegalStateException() {
        assertThatThrownBy(() -> PermissionSyntax.validateAndFreeze(List.of("home.view")))
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

    @Test
    void recognized_purelyLocalPermission_isDropped() {
        // Sanitised permissions become Spring granted authorities. A formal local permission must not
        // appear among them, or a later @PreAuthorize("hasAuthority('workspace:update')") would look
        // enforced while the real check ignores holdings entirely.
        assertThat(Permissions.recognized(Set.of(LocalPermissions.Workspace.UPDATE))).isEmpty();
    }

    @Test
    void recognized_nullOrEmpty_returnsEmptySet() {
        assertThat(Permissions.recognized(null)).isEmpty();
        assertThat(Permissions.recognized(Set.of())).isEmpty();
    }

    @Test
    void declarations_areIndependent() {
        // Neither declaration reads the other's statics, so no initialization order can leave one of them
        // holding a null or partial set.
        assertThat(LocalPermissions.ALL).isNotEmpty();
        assertThat(Permissions.ALL).isNotEmpty();
    }
}
