package vg.rg.security.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPermissionsTest {

    @Test
    void all_exposesStableCatalog() {
        assertThat(LocalPermissions.ALL).containsExactly(
                LocalPermissions.Location.READ,
                LocalPermissions.Location.LIST,
                LocalPermissions.Location.CREATE,
                LocalPermissions.Location.UPDATE,
                LocalPermissions.Location.DELETE,
                LocalPermissions.Workspace.CREATE,
                LocalPermissions.Workspace.READ,
                LocalPermissions.Workspace.UPDATE,
                LocalPermissions.Workspace.DELETE);
    }

    @Test
    void everyDeclaredPermission_isWellFormedAndRecognized() {
        assertThat(LocalPermissions.ALL).allSatisfy(permission -> {
            assertThat(Permissions.hasValidFormat(permission)).isTrue();
            assertThat(LocalPermissions.isRecognized(permission)).isTrue();
        });
    }

    @Test
    void isRecognized_appWidePermission_returnsFalse() {
        // The resource-scoped check accepts only local permissions; an app-wide one must be rejected
        // rather than silently treated as scoped.
        assertThat(LocalPermissions.isRecognized(Permissions.Workspace.OWNER)).isFalse();
        assertThat(LocalPermissions.isRecognized(Permissions.Reports.READ)).isFalse();
        assertThat(LocalPermissions.isRecognized(Permissions.Request.SUBMIT)).isFalse();
    }

    @Test
    void isRecognized_unknownOrNull_returnsFalse() {
        assertThat(LocalPermissions.isRecognized("unknown:view")).isFalse();
        assertThat(LocalPermissions.isRecognized(null)).isFalse();
    }

    @Test
    void locationContains_claimsOnlyLocationPermissions() {
        assertThat(LocalPermissions.Location.contains(LocalPermissions.Location.READ)).isTrue();
        assertThat(LocalPermissions.Location.contains(LocalPermissions.Location.LIST)).isTrue();
        assertThat(LocalPermissions.Location.contains(LocalPermissions.Location.CREATE)).isTrue();
        assertThat(LocalPermissions.Location.contains(LocalPermissions.Location.UPDATE)).isTrue();
        assertThat(LocalPermissions.Location.contains(LocalPermissions.Location.DELETE)).isTrue();

        assertThat(LocalPermissions.Location.contains(LocalPermissions.Workspace.UPDATE)).isFalse();
        assertThat(LocalPermissions.Location.contains(Permissions.Workspace.OWNER)).isFalse();
        assertThat(LocalPermissions.Location.contains(null)).isFalse();
    }

    @Test
    void workspaceContains_claimsOnlyWorkspacePermissions() {
        assertThat(LocalPermissions.Workspace.contains(LocalPermissions.Workspace.CREATE)).isTrue();
        assertThat(LocalPermissions.Workspace.contains(LocalPermissions.Workspace.READ)).isTrue();
        assertThat(LocalPermissions.Workspace.contains(LocalPermissions.Workspace.UPDATE)).isTrue();
        assertThat(LocalPermissions.Workspace.contains(LocalPermissions.Workspace.DELETE)).isTrue();

        assertThat(LocalPermissions.Workspace.contains(LocalPermissions.Location.UPDATE)).isFalse();
        assertThat(LocalPermissions.Workspace.contains(null)).isFalse();
    }

    @Test
    void contains_isSetMembershipNotStringParsing() {
        // A near-miss must claim nothing. Parsing the prefix would read "locationn" as a type.
        assertThat(LocalPermissions.Location.contains("locationn:update")).isFalse();
        assertThat(LocalPermissions.Location.contains("location:updat")).isFalse();
        assertThat(LocalPermissions.Location.contains("location:")).isFalse();
        assertThat(LocalPermissions.Workspace.contains("workspace:owner")).isFalse();
    }

    @Test
    void groupsAreDisjoint() {
        assertThat(LocalPermissions.Location.ALL)
                .doesNotContainAnyElementsOf(LocalPermissions.Workspace.ALL);
    }

    @Test
    void addressesContainer_createAndListVerbsOfContainedTypes() {
        // create has no resource yet; list's subject is a collection scoped to its container. Both
        // therefore travel with a container identifier.
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Location.CREATE)).isTrue();
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Location.LIST)).isTrue();

        // A workspace is a chain root with no container, so its create verb is not container-addressed:
        // there would be nothing for a container lookup to find.
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Workspace.CREATE)).isFalse();

        // read addresses one location by its own identifier, so it is not container-addressed.
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Location.READ)).isFalse();
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Location.UPDATE)).isFalse();
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Location.DELETE)).isFalse();
        assertThat(LocalPermissions.addressesContainer(LocalPermissions.Workspace.UPDATE)).isFalse();
        assertThat(LocalPermissions.addressesContainer(null)).isFalse();
    }

    @Test
    void all_mutationAttempt_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> LocalPermissions.ALL.add("other:view"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> LocalPermissions.Location.ALL.add("other:view"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
