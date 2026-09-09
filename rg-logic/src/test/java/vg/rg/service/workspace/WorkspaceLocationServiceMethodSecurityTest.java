package vg.rg.service.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.config.GeoProperties;
import vg.rg.mapper.workspace.WorkspaceLocationMapper;
import vg.rg.model.geo.LocationModel;
import vg.rg.model.geo.ProximityQuery;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.security.WorkspaceScope;
import vg.rg.repository.workspace.WorkspaceLocationRepository;
import vg.rg.service.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Method-security coverage for {@link WorkspaceLocationService}: which callers the guards admit, and —
 * the point of the whole layer — that <strong>owning the workspace is enough</strong>, with no
 * per-capability grant needed inside it.
 */
class WorkspaceLocationServiceMethodSecurityTest {

    private static final UniqueId OWNER = new UniqueId(3001L);
    private static final UniqueId STRANGER = new UniqueId(3002L);
    private static final UniqueId WORKSPACE = new UniqueId(5001L);
    private static final ProximityQuery QUERY =
            new ProximityQuery(BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reads_missingAuthentication_areDenied() {
        withService(service -> {
            assertThatThrownBy(() -> service.browse(WORKSPACE, PageRequest.of(0, 10)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.findNearby(WORKSPACE, QUERY))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.searchByName(WORKSPACE, "x", 5))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void everyMethod_missingWorkspaceOwnerGate_isDenied() {
        // Holding every location capability but not the layer gate must deny: the feature is opt-in.
        withService(service -> {
            authenticate(OWNER, LocalPermissions.Location.ALL);

            assertThatThrownBy(() -> service.browse(WORKSPACE, PageRequest.of(0, 10)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() ->
                    service.create(WORKSPACE, LocationModel.builder().name("Depot").build()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.delete(new UniqueId(9L)))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void everyMethod_workspaceOwnedByAnotherUser_isDenied() {
        withService(service -> {
            authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));

            assertThatThrownBy(() -> service.browse(WORKSPACE, PageRequest.of(0, 10)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() ->
                    service.create(WORKSPACE, LocationModel.builder().name("Depot").build()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.searchByName(WORKSPACE, "x", 5))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void ownerHoldingNoLocationCapabilities_isAllowedEverything() {
        // The property the layer rests on: ownership grants complete authority over the workspace's
        // contents, so the owner needs no location:* grant of their own.
        withService(service -> {
            authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));

            assertThatCode(() -> service.browse(WORKSPACE, PageRequest.of(0, 10)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> service.searchByName(WORKSPACE, "x", 5)).doesNotThrowAnyException();
            assertThatCode(() -> service.findNearby(WORKSPACE, QUERY)).doesNotThrowAnyException();
            assertThatCode(() ->
                    service.create(WORKSPACE, LocationModel.builder().name("Depot").build()))
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void ownerWithoutTheLayerGate_cannotBeRescuedByCapabilities() {
        withService(service -> {
            authenticate(OWNER, LocalPermissions.ALL);

            assertThatThrownBy(() -> service.searchByName(WORKSPACE, "x", 5))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void resolverIsGivenTheResourceIdentifier_notTheWorkspace() {
        // update/delete pass the location's own id; the resolver finds the workspace. A call site must
        // never resolve the workspace itself.
        var resolver = new RecordingResolver();
        withService(resolver, service -> {
            authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));
            var locationId = new UniqueId(6001L);

            assertThatCode(() -> service.delete(locationId)).doesNotThrowAnyException();

            assertThat(resolver.lastResourceId).isEqualTo(locationId);
            assertThat(resolver.lastPermission).isEqualTo(LocalPermissions.Location.DELETE);
        });
    }

    @Test
    void createIsGivenTheContainerIdentifier() {
        var resolver = new RecordingResolver();
        withService(resolver, service -> {
            authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));

            assertThatCode(() ->
                    service.create(WORKSPACE, LocationModel.builder().name("Depot").build()))
                    .doesNotThrowAnyException();

            assertThat(resolver.lastResourceId).isEqualTo(WORKSPACE);
            assertThat(resolver.lastPermission).isEqualTo(LocalPermissions.Location.CREATE);
        });
    }

    private void withService(java.util.function.Consumer<WorkspaceLocationService> body) {
        withService(new OwnerResolver(), body);
    }

    private void withService(WorkspaceScopeResolver resolver,
                             java.util.function.Consumer<WorkspaceLocationService> body) {
        TestConfiguration.resolver = resolver;
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            body.accept(context.getBean(WorkspaceLocationService.class));
        }
    }

    private static void authenticate(UniqueId user, Set<String> permissions) {
        var principal = AuthenticatedUserPrincipal.builder()
                .userUniqueId(user)
                .permissions(permissions)
                .authenticationFlow(AuthenticationFlow.TELEGRAM)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    /** Resolves everything to a workspace owned by {@link #OWNER}. */
    private static class OwnerResolver implements WorkspaceScopeResolver {
        @Override
        public Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission) {
            return Optional.of(new WorkspaceScope(WORKSPACE, OWNER));
        }
    }

    /** Records what the guard handed the resolver, so the call-site contract can be asserted. */
    private static class RecordingResolver implements WorkspaceScopeResolver {
        private UniqueId lastResourceId;
        private String lastPermission;

        @Override
        public Optional<WorkspaceScope> resolve(UniqueId resourceId, String permission) {
            this.lastResourceId = resourceId;
            this.lastPermission = permission;
            return Optional.of(new WorkspaceScope(WORKSPACE, OWNER));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfiguration {

        static WorkspaceScopeResolver resolver;

        @Bean
        AuthorityChecker authorityChecker() {
            return new AuthorityChecker(resolver);
        }

        @Bean
        WorkspaceLocationService workspaceLocationService(WorkspaceLocationRepository repository,
                                                          WorkspaceLocationMapper mapper) {
            return new WorkspaceLocationServiceImpl(
                    Mockito.mock(UniqueIdService.class), repository, mapper,
                    new GeoProperties("500", "50"));
        }

        @Bean
        WorkspaceLocationRepository workspaceLocationRepository() {
            var repository = Mockito.mock(WorkspaceLocationRepository.class);
            Mockito.when(repository.findByWorkspaceUniqueId(any(), any())).thenReturn(Page.empty());
            Mockito.when(repository.findByWorkspaceUniqueIdAndNameContainingIgnoreCase(
                    any(), any(), any())).thenReturn(Page.empty());
            Mockito.when(repository.findWithinBoundingBox(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            Mockito.when(repository.findById(any(UniqueId.class)))
                    .thenReturn(Optional.of(vg.rg.entity.workspace.WorkspaceLocationEntity.builder()
                            .uniqueId(6001L)
                            .workspaceUniqueId(WORKSPACE)
                            .name("Existing")
                            .build()));
            return repository;
        }

        @Bean
        WorkspaceLocationMapper workspaceLocationMapper() {
            var mapper = Mockito.mock(WorkspaceLocationMapper.class);
            Mockito.when(mapper.toEntity(any()))
                    .thenReturn(vg.rg.entity.workspace.WorkspaceLocationEntity.builder().name("Depot").build());
            Mockito.when(mapper.toModel(any())).thenReturn(LocationModel.builder().name("Depot").build());
            return mapper;
        }
    }
}
