package vg.rg.frontend.vaadin.component.scroll;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scroll-cue component's own contract, tested once rather than inferred from its callers.
 *
 * <p>The cue <em>toggling</em> is client-side JavaScript keyed off the viewport's scroll position, and a
 * unit test has no layout engine to scroll — so what is verified here is everything the toggling depends
 * on and everything a caller relies on: the DOM shape (a wrapper holding two cues and a viewport), the
 * cues being hidden from assistive technology (they are a sighted convenience; the content is reachable
 * without them), content being routed into the viewport rather than beside the cues, and — the failure
 * that is otherwise silent — every declared CSS class actually existing in the stylesheet.
 */
class ScrollCueTest {

    @Test
    void newInstance_isAWrapperHoldingAnUpCue_theViewport_andADownCue() {
        var cue = new ScrollCue();

        assertThat(cue.hasClassName(ScrollCue.WRAPPER)).isTrue();
        // A cue is present at each edge, and the viewport sits between them.
        assertThat(withClass(cue, ScrollCue.CUE_UP)).hasSize(1);
        assertThat(withClass(cue, ScrollCue.CUE_DOWN)).hasSize(1);
        assertThat(withClass(cue, ScrollCue.VIEWPORT)).hasSize(1);
    }

    /**
     * The cues carry no information the content does not — they are a "there is more this way" hint for
     * sighted users, and the rows themselves remain reachable by keyboard and screen reader. Announcing
     * two decorative chevrons would only be noise, so both are hidden from assistive technology.
     */
    @Test
    void bothCues_areHiddenFromAssistiveTechnology() {
        var cue = new ScrollCue();

        assertThat(withClass(cue, ScrollCue.CUE))
                .hasSize(2)
                .allSatisfy(chevron ->
                        assertThat(chevron.getElement().getAttribute("aria-hidden")).isEqualTo("true"));
    }

    @Test
    void addContent_putsChildrenInTheViewport_notBesideTheCues() {
        var cue = new ScrollCue();
        var first = new Paragraph("first");
        var second = new Paragraph("second");

        cue.addContent(first, second);

        // The content lands inside the viewport, so it scrolls with the list rather than overlaying it.
        assertThat(cue.viewport().getChildren()).containsExactly(first, second);
        assertThat(descendants(cue)).contains(first, second);
    }

    @Test
    void clearContent_emptiesTheViewportButKeepsTheCues() {
        var cue = new ScrollCue();
        cue.addContent(new Paragraph("row"));

        cue.clearContent();

        assertThat(cue.viewport().getChildren()).isEmpty();
        // The scroll shell itself survives a content refresh: the cues are structural, not content.
        assertThat(withClass(cue, ScrollCue.CUE_UP)).hasSize(1);
        assertThat(withClass(cue, ScrollCue.CUE_DOWN)).hasSize(1);
    }

    /**
     * Every class name this component declares must exist in the stylesheet.
     *
     * <p>The failure this guards is silent in both directions: a constant misspelled here matches no rule
     * and the element renders unstyled, and a rule renamed in the stylesheet leaves the constant pointing
     * at nothing — neither breaks a build or any other test, the scroll shell just quietly loses its
     * styling. The match is on a whole selector token, not a substring: {@code .scroll-cue} is a prefix of
     * {@code .scroll-cue__viewport}, so a plain {@code contains} would accept a dropped suffix — exactly
     * the likeliest typo.
     */
    @Test
    void everyDeclaredClassNameExistsInTheStylesheet() throws IOException {
        var stylesheet = Files.readString(repositoryRoot().resolve(
                "rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css"));

        var declared = Arrays.stream(ScrollCue.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalStateException("Cannot read " + field.getName(), exception);
                    }
                })
                .toList();

        assertThat(declared).isNotEmpty();
        assertThat(declared).allSatisfy(className ->
                assertThat(stylesheet)
                        .as("stylesheet rule for .%s", className)
                        .containsPattern("\\." + Pattern.quote(className) + "(?![A-Za-z0-9_-])"));
    }

    // --- traversal ----------------------------------------------------------------------------------

    private static List<Component> withClass(Component root, String className) {
        return descendants(root).stream()
                .filter(component -> component.hasClassName(className))
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
