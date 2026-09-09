package vg.rg.frontend.vaadin.view;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.workspace.WorkspaceLocationsView;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceSelectionService;
import vg.unique.id.model.UniqueId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The global location collection is gone from the user interface: no route, no navigation entry, no view.
 *
 * <p>Worth its own test because the removal is what makes the workspace scope real. A leftover
 * {@code /locations} screen would keep serving an unscoped collection whose authority model no longer
 * exists, and would do so silently — nothing else in the suite would notice.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalScopeRetiredTest {

    private static final Path FRONTEND_MAIN =
            repositoryRoot().resolve("rg-frontend-vaadin/src/main/java");

    private static final UniqueId WORKSPACE = new UniqueId(5001L);

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceSelectionService selectionService;

    @Test
    void theGlobalLocationsViewNoLongerExists() {
        assertThatThrownBy(() -> Class.forName("vg.rg.frontend.vaadin.view.LocationsView"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noRouteServesAGlobalLocationCollection() throws IOException {
        // Read from source rather than the route registry: the registry is populated by the Vaadin
        // servlet, so an unregistered-but-present @Route would pass a registry check and still resolve
        // in a running application.
        var production = textUnder(FRONTEND_MAIN);

        assertThat(production).doesNotContain("@Route(value = \"locations\"", "@Route(\"locations\")");
        // The workspace-scoped screen keeps its own nested route, which is the only locations route left.
        assertThat(WorkspaceLocationsView.class.getAnnotation(Route.class).value())
                .isEqualTo("workspaces/locations");
    }

    @Test
    void noProductionCodeNavigatesToTheRetiredPath() throws IOException {
        var production = textUnder(FRONTEND_MAIN);

        assertThat(production).doesNotContain("\"/locations\"", "navigate(\"locations\")");
    }

    @Test
    void theOnlyLocationsEntryPointsAtTheWorkspaceScopedRoute() {
        // The entry is top level again, but what it opens is the workspace-scoped screen. The retired
        // global route is what must stay unreachable, not the word "locations" in the drawer.
        var view = mainViewFor(Permissions.APP_WIDE);

        assertThat(view.navigationLabels()).contains("nav.locations");
        assertThat(view.navigationPaths())
                .anySatisfy(path -> assertThat(path).contains("workspaces/locations"));
        assertThat(view.navigationPaths())
                .noneSatisfy(path -> assertThat(path).isIn("locations", "/locations"));
    }

    @Test
    void reachingALocationStillRequiresAWorkspace() {
        // The structural consequence of the retirement: a user with no workspace permission has no
        // workspace to scope a location query to, so there is no path to a location at all.
        var view = mainViewFor(Set.of(Permissions.Reports.READ, Permissions.Request.SUBMIT));

        assertThat(view.navigationLabels()).doesNotContain("nav.locations");
    }

    private MainView mainViewFor(Set<String> permissions) {
        when(localization.getProvidedLocales())
                .thenReturn(List.of(LocalizationService.DEFAULT_LOCALE, Locale.ENGLISH));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal));
        when(selectionService.activeWorkspace())
                .thenReturn(WorkspaceModel.builder().uniqueId(WORKSPACE).defaultWorkspace(true).build());
        when(authorityChecker.hasAuthority(WORKSPACE, LocalPermissions.Location.LIST)).thenReturn(true);

        return new MainView(localization, authenticationContext, authorityChecker, selectionService);
    }

    private static String textUnder(Path root) throws IOException {
        var result = new StringBuilder();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            result.append(Files.readString(path)).append('\n');
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect authored source", exception);
                        }
                    });
        }
        return result.toString();
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
