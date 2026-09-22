package vg.rg.entity.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards generic publication constraints expressed only in Liquibase. */
class ResourcePublicationSchemaTest {

    private static final String CHANGESET = "rg-logic/src/main/resources/db/liquibase/007-event-visibility-membership.yaml";

    @Test
    void changeset_movesPublicationAndRulesToGenericResourcesWithoutEventForeignKeys() throws IOException {
        var declarations = Files.readString(repositoryRoot().resolve(CHANGESET));

        assertThat(declarations).contains(
                "tableName: rg_workspace_event\n            columnName: is_published",
                "tableName: rg_resource_publication",
                "name: resource_unique_id\n                  type: BIGINT",
                "name: visible_since\n                  type: DATETIME(8)",
                "name: visibility_mode\n                  type: TINYINT\n                  defaultValueNumeric: 0",
                "name: author\n                  type: BIGINT",
                "name: last_editor\n                  type: BIGINT",
                "name: created_at\n                  type: DATETIME(8)",
                "name: updated_at\n                  type: DATETIME(8)",
                "tableName: rg_resource_visibility_rule",
                "name: effect\n                  type: TINYINT",
                "name: subject_kind\n                  type: TINYINT",
                "fk_rg_resource_visibility_rule_publication",
                "uq_rg_resource_visibility_rule_target");
        assertThat(declarations).doesNotContain("rg_workspace_event_visibility_rule", "onDelete", "deleteCascade");
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) candidate = candidate.getParent();
        if (candidate == null) throw new IllegalStateException("Cannot locate repository root");
        return candidate;
    }
}
