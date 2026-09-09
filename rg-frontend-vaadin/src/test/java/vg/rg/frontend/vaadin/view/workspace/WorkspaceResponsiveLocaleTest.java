package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.Permissions;
import vg.rg.model.workspace.WorkspaceModel;
import vg.rg.service.security.AuthorityChecker;
import vg.rg.service.workspace.WorkspaceService;
import vg.unique.id.model.UniqueId;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Presentation gates for the workspace flows: mobile-first layout, and nothing that only reads correctly
 * in one language.
 *
 * <p>A unit test cannot measure a rendered viewport, so it asserts the two things that actually decide
 * the narrow-screen outcome and are checkable here: the stylesheet reaches wider layouts through
 * {@code min-width} queries only — a {@code max-width} breakpoint is a desktop-first design wearing a
 * mobile-first coat — and no workspace component pins itself to a pixel width that a 320px screen cannot
 * hold. The visual walk-through stays a manual step in the quickstart.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceResponsiveLocaleTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path STYLESHEET =
            ROOT.resolve("rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css");

    /** The narrowest width the stylesheet commits to supporting. */
    private static final int NARROWEST_SUPPORTED_WIDTH_PX = 320;

    /** {@code rg.workspace.name-max-length}: a name may be this long with no spaces to break at. */
    private static final int NAME_MAX_LENGTH = 128;

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock WorkspaceService workspaceService;
    @Mock BeforeEnterEvent event;

    private UI ui;

    @BeforeEach
    void attachUi() {
        ui = new UI();
        UI.setCurrent(ui);
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    @Test
    void everyWorkspaceRuleIsStyled() throws IOException {
        // A class name applied in Java with no rule behind it is not "default styling" -- it is a
        // stylesheet that silently stopped covering the view.
        var stylesheet = Files.readString(STYLESHEET);
        var applied = appliedClassNames();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(className ->
                assertThat(stylesheet).as("no rule for .%s", className).contains("." + className));
    }

    @Test
    void wideLayoutsArriveOnlyThroughMinWidthQueries() throws IOException {
        var stylesheet = Files.readString(STYLESHEET);

        var breakpoints = Pattern.compile("@media\\s*\\(([^)]*width[^)]*)\\)").matcher(stylesheet)
                .results()
                .map(match -> match.group(1).trim())
                .toList();

        assertThat(breakpoints).isNotEmpty();
        assertThat(breakpoints).allSatisfy(breakpoint ->
                assertThat(breakpoint).as("desktop-first breakpoint").startsWith("min-width"));
    }

    @Test
    void theNarrowestSupportedWidthIsDeclaredAndNotExceededByAnyWorkspaceComponent() throws IOException {
        var stylesheet = Files.readString(STYLESHEET);
        assertThat(stylesheet).contains("min-width: " + NARROWEST_SUPPORTED_WIDTH_PX + "px");

        // Pixel widths in the workspace views: anything fixed and wider than the narrowest screen would
        // force horizontal scrolling there, whatever the stylesheet says.
        var fixedWidths = Pattern.compile("setWidth\\(\"(\\d+)px\"\\)")
                .matcher(workspaceViewSource())
                .results()
                .map(match -> Integer.parseInt(match.group(1)))
                .filter(width -> width > NARROWEST_SUPPORTED_WIDTH_PX)
                .toList();

        assertThat(fixedWidths).isEmpty();
    }

    @Test
    void aMaximumLengthNameIsRenderedInFullAndNeverPinnedToAFixedWidth() {
        var longest = "W".repeat(NAME_MAX_LENGTH);
        var view = enteredWorkspacesView(named(longest));

        // Rendered in full: truncating in the markup would hide the user's own text rather than wrapping
        // it, and no amount of CSS could recover it.
        assertThat(texts(view, Span.class)).contains(longest);
        assertThat(descendants(view))
                .filteredOn(HasSize.class::isInstance)
                .allSatisfy(component -> {
                    var width = ((HasSize) component).getWidth();
                    assertThat(width == null || !width.endsWith("px"))
                            .as("fixed pixel width %s", width)
                            .isTrue();
                });
    }

    @Test
    void aSystemNamedWorkspaceLabelComesFromAKeyThatDiffersBetweenLocales() throws IOException {
        // The reason a system-created workspace stores no name at all. If the two bundles carried the
        // same text for this key, the label would look locale-following while being nothing of the kind.
        var ukrainian = load("messages.properties");
        var english = load("messages_en.properties");

        for (var key : List.of("workspace.default.name", "workspace.default.badge")) {
            assertThat(ukrainian.getProperty(key)).as("uk %s", key).isNotBlank();
            assertThat(english.getProperty(key)).as("en %s", key).isNotBlank();
            assertThat(ukrainian.getProperty(key))
                    .as("%s is not actually translated", key)
                    .isNotEqualTo(english.getProperty(key));
        }
    }

    @Test
    void everyWorkspaceStringIsTranslatedInBothLocales() throws IOException {
        // No missing-key fallback: the raw key would be what a user sees.
        var ukrainian = load("messages.properties");
        var english = load("messages_en.properties");

        var workspaceKeys = ukrainian.stringPropertyNames().stream()
                .filter(key -> key.startsWith("workspace") || key.startsWith("nav.workspace")
                        || key.startsWith("page.workspace"))
                .toList();

        assertThat(workspaceKeys).isNotEmpty();
        assertThat(workspaceKeys).allSatisfy(key -> {
            assertThat(ukrainian.getProperty(key)).as("uk %s", key).isNotBlank();
            assertThat(english.getProperty(key)).as("en %s", key).isNotBlank();
        });
    }

    // ------------------------------------------------------------------------------------ fixtures

    private WorkspacesView enteredWorkspacesView(WorkspaceModel... owned) {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorityChecker.hasAuthority(Permissions.Workspace.OWNER)).thenReturn(true);
        when(workspaceService.listOwned()).thenReturn(List.of(owned));

        var view = new WorkspacesView(localization, authorityChecker, workspaceService);
        view.beforeEnter(event);
        return view;
    }

    private static WorkspaceModel named(String name) {
        return WorkspaceModel.builder().uniqueId(new UniqueId(6001L)).name(name).build();
    }

    /** Every {@code workspace-*} class name the views actually apply. */
    private static List<String> appliedClassNames() throws IOException {
        return Pattern.compile("addClassName\\(\"(workspace-[a-z-]+)\"\\)")
                .matcher(workspaceViewSource())
                .results()
                .map(match -> match.group(1))
                .distinct()
                .sorted()
                .toList();
    }

    private static String workspaceViewSource() throws IOException {
        var views = ROOT.resolve("rg-frontend-vaadin/src/main/java/vg/rg/frontend/vaadin/view");
        try (Stream<Path> files = Files.walk(views)) {
            var result = new StringBuilder();
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("Workspace"))
                    .forEach(path -> {
                        try {
                            result.append(Files.readString(path)).append('\n');
                        } catch (IOException exception) {
                            throw new IllegalStateException("Cannot inspect authored source", exception);
                        }
                    });
            return result.toString();
        }
    }

    private Properties load(String name) throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as(name).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static <T extends Component> List<String> texts(Component root, Class<T> type) {
        return descendants(root).stream()
                .filter(type::isInstance)
                .map(component -> component.getElement().getText())
                .toList();
    }

    private static List<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child).stream()))
                .toList();
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
