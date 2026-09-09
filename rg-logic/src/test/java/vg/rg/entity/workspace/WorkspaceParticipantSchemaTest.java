package vg.rg.entity.workspace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions about the participant changeset that only its text can carry — Liquibase applies it
 * identically whether or not these hold, so nothing else would notice a regression.
 *
 * <p>Two properties, for two different reasons.
 *
 * <p><strong>No person-shaped column.</strong> Someone reading this schema must not be able to see a
 * person, so the label and the number live inside one opaque encrypted column and nothing is named after
 * what it holds. The temptation this guards against is a well-meaning "just add a plaintext
 * {@code label} column, it is only a display name" — which the amended Principle I does not permit.
 *
 * <p><strong>No {@code onDelete} on the foreign key.</strong> The restrictiveness is load-bearing rather
 * than incidental: it is what makes a contained type whose {@code WorkspaceContentContributor} is missing
 * <em>fail loudly</em> instead of having its rows silently swept away with no audit trail, no
 * confirmation count and no opportunity to refuse. Adding a cascade here would quietly convert a test
 * failure into data loss nobody notices.
 */
class WorkspaceParticipantSchemaTest {

    private static final String CHANGESET = "rg-logic/src/main/resources/db/liquibase/"
            + "004-workspace-participant.yaml";

    @Test
    void changeset_declaresNoPersonShapedColumn() throws IOException {
        var columns = columnNames();

        assertThat(columns).isNotEmpty().contains("descriptor");
        assertThat(columns).allSatisfy(column -> assertThat(column)
                .as("column %s looks like personal data", column)
                .doesNotContain("label")
                .doesNotContain("name")
                .doesNotContain("phone")
                .doesNotContain("email")
                .doesNotContain("contact")
                .doesNotContain("telegram"));
    }

    @Test
    void descriptorColumn_isBinary() throws IOException {
        // A text column would mean somebody made it readable.
        assertThat(declarationsOnly()).contains("name: descriptor").contains("type: BLOB");
    }

    @Test
    void foreignKeyToTheWorkspace_declaresNoCascade() throws IOException {
        // Declarations only. The comments in that file discuss onDelete at length, deliberately, and a
        // naive text search would read the explanation as the thing it warns against.
        var declarations = declarationsOnly();

        assertThat(declarations).contains("fk_rg_workspace_participant_workspace");
        assertThat(declarations).doesNotContain("onDelete");
        assertThat(declarations).doesNotContain("deleteCascade");
    }

    /**
     * Ciphertext is non-deterministic — a fresh IV per write — so an index or unique constraint over the
     * descriptor could never match two equal values. One declared here would not fail; it would silently
     * never fire, which is worse.
     */
    @Test
    void noIndexOrConstraintCoversTheDescriptor() throws IOException {
        var declarations = declarationsOnly();
        var descriptorIndexed = declarations.lines()
                .map(String::strip)
                .filter(line -> line.equals("name: descriptor"))
                .count();

        assertThat(descriptorIndexed)
                .as("the descriptor appears once, as a column definition and not in an index")
                .isEqualTo(1);
        assertThat(declarations).doesNotContain("addUniqueConstraint");
    }

    private static java.util.List<String> columnNames() throws IOException {
        return declarationsOnly().lines()
                .map(String::strip)
                .filter(line -> line.startsWith("name: "))
                .map(line -> line.substring("name: ".length()))
                .filter(name -> !name.startsWith("fk_") && !name.startsWith("ix_")
                        && !name.startsWith("pk_"))
                .toList();
    }

    /** The changeset with YAML comments removed, so prose about the schema is not read as schema. */
    private static String declarationsOnly() throws IOException {
        return Files.readString(repositoryRoot().resolve(CHANGESET)).lines()
                .filter(line -> !line.strip().startsWith("#"))
                .reduce(new StringBuilder(), (buffer, line) -> buffer.append(line).append('\n'),
                        StringBuilder::append)
                .toString();
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
