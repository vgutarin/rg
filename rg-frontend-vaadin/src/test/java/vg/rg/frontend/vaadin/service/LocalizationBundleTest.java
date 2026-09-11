package vg.rg.frontend.vaadin.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationBundleTest {

    private static final Path ROOT = repositoryRoot();

    /**
     * Every literal key the application asks for by name.
     *
     * <p>{@code getTranslation} counts as well as {@code i18n}: a key with placeholders has to go
     * through the parameterised overload, so a family looked up only that way would otherwise be
     * invisible here.
     *
     * <p>Each alternative requires its own terminator, and that is what keeps <em>composed</em> keys
     * out: {@code i18n("permission." + p)} has no {@code ")} after the literal, and a composed
     * {@code getTranslation} argument has no {@code ",}. A composed key cannot be resolved from its
     * prefix, so matching one here would report a fragment as a missing translation.
     */
    private static final Pattern LITERAL_LOOKUP = Pattern.compile(
            "(?:i18n\\(\"([^\"]+)\"\\)"
                    + "|getTranslation\\(\"([^\"]+)\","
                    + "|@PageTitle\\(\"([^\"]+)\"\\))");

    /**
     * A dot-separated lowercase token — the shape of a key in these bundles. Used to decide whether an
     * unresolved string literal was meant as a key at all, since some lookups pass plain display text.
     */
    private static final Pattern KEY_SHAPED = Pattern.compile("[a-z][a-z0-9-]*(?:[.:][a-z0-9-]+)+");

    @Test
    void load_ukrainianAndEnglishBundles_returnsIdenticalKeys() throws IOException {
        var ukrainian = load("messages.properties");
        var english = load("messages_en.properties");

        assertThat(english.keySet()).isEqualTo(ukrainian.keySet());
    }

    @Test
    void load_ukrainianBundle_returnsNonblankValues() throws IOException {
        var ukrainian = load("messages.properties");

        assertThat(ukrainian).allSatisfy((key, value) -> assertThat(value.toString()).isNotBlank());
    }

    @Test
    void load_englishBundle_returnsNonblankValues() throws IOException {
        var english = load("messages_en.properties");

        assertThat(english).allSatisfy((key, value) -> assertThat(value.toString()).isNotBlank());
    }

    /**
     * Key parity says the two bundles agree with each other; it says nothing about whether they agree
     * with the code. This is the half that catches a key the application asks for and neither bundle
     * has — which a user sees as the raw key echoed back at them.
     */
    @Test
    void everyLiteralLookupResolvesInBothBundles() throws IOException {
        var ukrainian = load("messages.properties").stringPropertyNames();
        var english = load("messages_en.properties").stringPropertyNames();

        var missing = literalLookupsInProduction().stream()
                .filter(key -> !ukrainian.contains(key) || !english.contains(key))
                .toList();

        assertThat(missing).isEmpty();
    }

    /**
     * The message keys the business layer returns as stable outcome codes. They never appear as a
     * literal at a lookup site — the UI translates whatever the exception carries — so nothing else
     * would notice their translation going missing.
     */
    @Test
    void everyBusinessOutcomeCodeIsTranslated() throws IOException {
        var ukrainian = load("messages.properties").stringPropertyNames();
        var english = load("messages_en.properties").stringPropertyNames();

        var codes = keyShapedLiteralsUnder(ROOT.resolve("rg-logic/src/main/java")).stream()
                .filter(literal -> literal.startsWith("workspace.error.")
                        || literal.startsWith("workspace.participant.error.")
                        || literal.startsWith("workspace.default."))
                .toList();

        assertThat(codes).isNotEmpty();
        assertThat(codes).allSatisfy(code -> {
            assertThat(ukrainian).as("uk translation for %s", code).contains(code);
            assertThat(english).as("en translation for %s", code).contains(code);
        });
    }

    /**
     * A key in the bundles that nothing asks for is dead weight that survives translation review. Only
     * dynamically composed families are exempt, because their names are built at runtime.
     */
    @Test
    void theBundlesCarryNoDeadWorkspaceKeys() throws IOException {
        // Production sources only. Counting test sources would let a key stay alive purely because a
        // test mentions it, which is exactly the kind of dead weight this is looking for.
        var named = new LinkedHashSet<String>(literalLookupsInProduction());
        named.addAll(keyShapedLiteralsUnder(ROOT.resolve("rg-frontend-vaadin/src/main/java")));
        named.addAll(keyShapedLiteralsUnder(ROOT.resolve("rg-logic/src/main/java")));

        var dead = load("messages.properties").stringPropertyNames().stream()
                .filter(key -> key.startsWith("workspace")
                        || key.startsWith("participant")
                        || key.startsWith("nav.workspace")
                        || key.startsWith("nav.participant"))
                .filter(key -> !named.contains(key))
                .toList();

        assertThat(dead).isEmpty();
    }

    /**
     * Keys the production code asks for at a lookup site. Test sources are excluded on purpose: a test
     * asserting that an <em>unknown</em> key is never looked up would otherwise register as a demand for
     * a translation of it.
     */
    private static Set<String> literalLookupsInProduction() throws IOException {
        var keys = new LinkedHashSet<String>();
        var matcher = LITERAL_LOOKUP.matcher(
                textUnder(ROOT.resolve("rg-frontend-vaadin/src/main/java")));
        while (matcher.find()) {
            // First non-null group, so adding another lookup form to the pattern needs no change here.
            for (var group = 1; group <= matcher.groupCount(); group++) {
                if (matcher.group(group) != null) {
                    keys.add(matcher.group(group));
                    break;
                }
            }
        }
        return keys;
    }

    private static Set<String> keyShapedLiteralsUnder(Path root) throws IOException {
        var literal = Pattern.compile("\"([^\"\\n]+)\"");
        var found = new LinkedHashSet<String>();
        var matcher = literal.matcher(textUnder(root));
        while (matcher.find()) {
            var value = matcher.group(1);
            if (KEY_SHAPED.matcher(value).matches()) {
                found.add(value);
            }
        }
        return found;
    }

    private static String textUnder(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            var result = new StringBuilder();
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
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

    private Properties load(String name) throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as(name).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
