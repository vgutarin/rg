package vg.rg.entity.workspace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceEventSchemaTest {

    private static final String CHANGESET = "rg-logic/src/main/resources/db/liquibase/005-workspace-event.yaml";

    @Test
    void changeset_hasTheEventColumnsAndRestrictiveForeignKeys() throws IOException {
        var declarations = Files.readString(repositoryRoot().resolve(CHANGESET));

        assertThat(declarations).contains("tableName: rg_workspace_event", "name: workspace_unique_id",
                "name: title", "type: VARCHAR(512)", "name: start_at", "name: end_at",
                "name: location_unique_id", "name: event_type", "type: TINYINT", "name: max_participant_count",
                "type: INT", "name: is_published",
                "type: BOOLEAN", "fk_rg_workspace_event_workspace", "fk_rg_workspace_event_location",
                "ix_rg_workspace_event_ws_title");
        assertThat(declarations).contains("name: location_unique_id\n                  type: BIGINT\n                  constraints:\n                    nullable: false");
        var workspaceForeignKey = declarations.substring(
                declarations.indexOf("constraintName: fk_rg_workspace_event_workspace"),
                declarations.indexOf("constraintName: fk_rg_workspace_event_location"));
        assertThat(workspaceForeignKey).doesNotContain("onDelete", "deleteCascade");
        var locationForeignKey = declarations.substring(
                declarations.indexOf("constraintName: fk_rg_workspace_event_location"),
                declarations.indexOf("indexName: ix_rg_workspace_event_ws_title"));
        assertThat(locationForeignKey).doesNotContain("onDelete", "deleteCascade");
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) candidate = candidate.getParent();
        if (candidate == null) throw new IllegalStateException("Cannot locate repository root");
        return candidate;
    }
}
