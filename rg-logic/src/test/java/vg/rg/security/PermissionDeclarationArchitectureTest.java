package vg.rg.security;

import org.junit.jupiter.api.Test;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the split between the two permission declarations, now that the global location scope is gone.
 *
 * <p>The split is only worth having if it cannot quietly re-merge. Two things would undo it: an app-wide
 * capability that is really workspace-scoped drifting back into {@link Permissions}, and a guard passing
 * a workspace-scoped capability to the flat, resource-less check — which would ask "does this user hold
 * {@code location:update}?" instead of "do they own the workspace this location is in?", a question with
 * no correct answer.
 *
 * <p>Source text rather than reflection for the call-site check, because what matters is which
 * <em>overload</em> a {@code @PreAuthorize} expression names, and that is a string until Spring parses it
 * at runtime (research R11).
 */
class PermissionDeclarationArchitectureTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path LOGIC_MAIN = ROOT.resolve("rg-logic/src/main/java");
    private static final Path FRONTEND_MAIN = ROOT.resolve("rg-frontend-vaadin/src/main/java");

    /**
     * A {@code hasAuthority(...)} whose first argument is a local permission. The absence of a comma
     * before the closing parenthesis is what distinguishes the flat overload from the resource-scoped
     * one, so that is what the pattern requires.
     */
    private static final Pattern FLAT_CHECK_WITH_LOCAL_PERMISSION = Pattern.compile(
            "hasAuthority\\((?![^)]*,)[^)]*"
                    + "(?:LocalPermissions|location:|workspace-participant:"
                    + "|workspace:(?:create|read|update|delete))");

    @Test
    void theAppWideDeclarationSharesNothingWithTheLocalOne() {
        assertThat(Permissions.APP_WIDE).isNotEmpty();
        assertThat(LocalPermissions.ALL).isNotEmpty();
        assertThat(Permissions.APP_WIDE).doesNotContainAnyElementsOf(LocalPermissions.ALL);
        assertThat(Permissions.ALL).doesNotContainAnyElementsOf(LocalPermissions.ALL);
    }

    @Test
    void theAppWideDeclarationDeclaresNoLocalCapability() throws IOException {
        var declaration = Files.readString(LOGIC_MAIN.resolve("vg/rg/security/model/Permissions.java"));

        // Literals, not symbols: a local capability re-entering this file would arrive as a string.
        assertThat(declaration).doesNotContain(
                "\"location:", "\"workspace-participant:", "\"workspace:create\"",
                "\"workspace:read\"", "\"workspace:update\"", "\"workspace:delete\"");
        // And the transitional escape hatch that let location capabilities sit in APP_WIDE is gone.
        assertThat(declaration).doesNotContain("TRANSITIONAL");
    }

    @Test
    void noProductionGuardPassesALocalPermissionToTheFlatCheck() throws IOException {
        var production = textUnder(LOGIC_MAIN) + textUnder(FRONTEND_MAIN);

        var offending = FLAT_CHECK_WITH_LOCAL_PERMISSION.matcher(production).results()
                .map(java.util.regex.MatchResult::group)
                .toList();

        assertThat(offending).isEmpty();
    }

    @Test
    void theRetiredGlobalLocationTypesAreGoneFromProduction() throws IOException {
        var production = textUnder(LOGIC_MAIN) + textUnder(FRONTEND_MAIN);

        // Fully qualified, so the workspace-scoped types of similar name are not matched by accident.
        assertThat(production).doesNotContain(
                "vg.rg.service.LocationService",
                "vg.rg.repository.LocationRepository",
                "vg.rg.repository.LegacyLocationRepository",
                "vg.rg.entity.LocationEntity",
                "vg.rg.mapper.LocationMapper");
        for (var retired : new String[] {
                "vg.rg.service.LocationService",
                "vg.rg.service.LocationServiceImpl",
                "vg.rg.repository.LocationRepository",
                "vg.rg.repository.LegacyLocationRepository",
                "vg.rg.entity.LocationEntity",
                "vg.rg.mapper.LocationMapper"}) {
            assertThatThrownBy(() -> Class.forName(retired))
                    .as("%s must no longer exist", retired)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void noProductionCodeReachesTheRetainedTableAtAll() throws IOException {
        // rg_location is retained as the rollback (research R10) but is now entirely unmapped: the
        // one-time migration that read it, and the read-only entity it read through, are both gone.
        // Nothing in either module may name it — not even in a comment, since a mention is the first
        // step towards a reader assuming it is live.
        assertThat(filesUnderMentioning(LOGIC_MAIN, "rg_location")).isEmpty();
        assertThat(filesUnderMentioning(FRONTEND_MAIN, "rg_location")).isEmpty();
    }

    @Test
    void theOneTimeMigrationIsGone() throws IOException {
        var production = textUnder(LOGIC_MAIN) + textUnder(FRONTEND_MAIN);

        assertThat(production).doesNotContain(
                "GlobalLocationMigration", "GLOBAL_LOCATION_TO_WORKSPACE", "WorkspaceMigrationMarker");
        // The startup gate went with it; a leftover property would read as a live switch.
        assertThat(production).doesNotContain("rg.workspace.migration.enabled");
        assertThat(Files.exists(LOGIC_MAIN.resolve("vg/rg/migration"))).isFalse();
    }

    /** Repository-relative paths of production files whose text mentions the needle. */
    private static java.util.List<String> filesUnderMentioning(Path root, String needle)
            throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(needle);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect authored source", exception);
                        }
                    })
                    .map(path -> root.relativize(path).toString())
                    .sorted()
                    .toList();
        }
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
