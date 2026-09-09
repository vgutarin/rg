package vg.rg.entity.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.BaseFuncTest;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.repository.workspace.WorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that changelog {@code 002-workspace-init.yaml} applies and that the constraints the design
 * relies on are actually enforced by the database rather than only by application code.
 */
class WorkspaceSchemaFuncTest extends BaseFuncTest {

    private static final UniqueId OWNER = new UniqueId(6001L);

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceLocationRepository locationRepository;

    @Autowired
    private UniqueIdService uniqueIdService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void authenticate() {
        var principal = new AuthenticatedUserPrincipal(
                OWNER, "Test Owner",
                Set.of(Permissions.Workspace.OWNER),
                true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        locationRepository.deleteAll();
        workspaceRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void oneDefaultWorkspacePerOwner_isEnforcedByTheDatabase() {
        // The nullable-unique marker column is what closes the concurrent-provisioning race: two
        // sessions racing to provision a default cannot both win.
        workspaceRepository.saveWithNewUniqueId(defaultWorkspace(OWNER), uniqueIdService);

        assertThatThrownBy(() ->
                workspaceRepository.saveAndFlush(
                        workspaceRepository.saveWithNewUniqueId(defaultWorkspace(OWNER), uniqueIdService)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void severalNonDefaultWorkspacesPerOwner_areAllowed() {
        // The same constraint must not forbid a second ordinary workspace -- which a unique index on
        // (owner, is_default) would have done.
        workspaceRepository.saveWithNewUniqueId(namedWorkspace(OWNER, "First"), uniqueIdService);
        workspaceRepository.saveWithNewUniqueId(namedWorkspace(OWNER, "Second"), uniqueIdService);
        workspaceRepository.flush();

        assertThat(workspaceRepository.countByOwnerUniqueId(OWNER)).isEqualTo(2);
    }

    @Test
    void differentOwners_mayEachHaveADefault() {
        var otherOwner = new UniqueId(6002L);
        workspaceRepository.saveWithNewUniqueId(defaultWorkspace(OWNER), uniqueIdService);
        workspaceRepository.saveWithNewUniqueId(defaultWorkspace(otherOwner), uniqueIdService);
        workspaceRepository.flush();

        assertThat(workspaceRepository.findByDefaultForOwner(OWNER)).isPresent();
        assertThat(workspaceRepository.findByDefaultForOwner(otherOwner)).isPresent();
    }

    @Test
    void systemNamedWorkspace_storesNoName() {
        // A stored name cannot follow the viewer's locale, so a system-created workspace stores none and
        // the UI renders a localized label instead.
        var saved = workspaceRepository.saveWithNewUniqueId(defaultWorkspace(OWNER), uniqueIdService);
        workspaceRepository.flush();

        assertThat(saved.getName()).isNull();
        assertThat(saved.isSystemNamed()).isTrue();
        assertThat(saved.isDefaultWorkspace()).isTrue();
    }

    @Test
    void locationWithoutWorkspace_isRejected() {
        // The mandatory column is what makes "a location outside a workspace" unrepresentable, so there
        // is no scope predicate for a query to forget.
        assertThatThrownBy(() -> {
            locationRepository.saveWithNewUniqueId(
                    WorkspaceLocationEntity.builder().name("Orphan").build(), uniqueIdService);
            locationRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void locationReferencingAnAbsentWorkspace_isRejectedByTheForeignKey() {
        assertThatThrownBy(() -> {
            locationRepository.saveWithNewUniqueId(
                    WorkspaceLocationEntity.builder()
                            .workspaceUniqueId(new UniqueId(424242L))
                            .name("Dangling")
                            .build(),
                    uniqueIdService);
            locationRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void retiredGlobalTable_stillExistsAndIsMarkedUnused() {
        // The changelog deliberately contains no dropTable: rg_location is the migration's rollback.
        var tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = database() and table_name = 'rg_location'",
                String.class);

        assertThat(tables).as("rg_location must be retained, not dropped").hasSize(1);

        var remarks = jdbcTemplate.queryForList(
                "select table_comment from information_schema.tables "
                        + "where table_schema = database() and table_name = 'rg_location'",
                String.class);

        assertThat(remarks).hasSize(1);
        assertThat(remarks.get(0))
                .as("a future reader must be able to tell a dead table from a live one")
                .contains("UNUSED")
                .contains("rg_workspace_location");
    }

    @Test
    void allNewTables_areCreated() {
        var expected = List.of(
                "rg_workspace", "rg_workspace_selection", "rg_workspace_location", "rg_migration_marker");

        for (var table : expected) {
            var found = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name = ?",
                    Integer.class, table);

            assertThat(found).as("table %s", table).isEqualTo(1);
        }
    }

    private static WorkspaceEntity defaultWorkspace(UniqueId owner) {
        return WorkspaceEntity.builder()
                .ownerUniqueId(owner)
                .defaultForOwner(owner)
                .build();
    }

    private static WorkspaceEntity namedWorkspace(UniqueId owner, String name) {
        return WorkspaceEntity.builder()
                .ownerUniqueId(owner)
                .name(name)
                .build();
    }
}
