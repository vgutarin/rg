package vg.rg.entity.workspace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards workspace membership constraints expressed only in Liquibase. */
class WorkspaceMembershipRequestSchemaTest {

    private static final String CHANGESET = "rg-logic/src/main/resources/db/liquibase/007-event-visibility-membership.yaml";

    @Test
    void changeset_keepsMembershipRequestsAndBoundParticipantsRestrictivelyLinked() throws IOException {
        var declarations = Files.readString(repositoryRoot().resolve(CHANGESET));

        assertThat(declarations).contains(
                "tableName: rg_workspace_membership_request",
                "name: descriptor", "type: BLOB",
                "fk_rg_workspace_membership_request_workspace",
                "fk_rg_workspace_membership_request_origin_event",
                "uq_rg_workspace_membership_request_pending_user",
                "uq_rg_workspace_participant_workspace_user");
        assertThat(declarations).doesNotContain("onDelete", "deleteCascade");
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) candidate = candidate.getParent();
        if (candidate == null) throw new IllegalStateException("Cannot locate repository root");
        return candidate;
    }
}
