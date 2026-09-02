package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.rg.config.WorkspaceProperties;
import vg.rg.entity.WorkspaceEntity;
import vg.rg.mapper.WorkspaceMapper;
import vg.rg.model.WorkspaceModel;
import vg.rg.repository.WorkspaceRepository;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.WorkspaceScope;
import vg.rg.security.WorkspaceScopeResolver;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class WorkspaceServiceMethodSecurityTest {

    private static final UniqueId OWNER = new UniqueId(3001L);
    private static final UniqueId STRANGER = new UniqueId(3002L);
    private static final UniqueId WORKSPACE = new UniqueId(5001L);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyMethod_missingAuthentication_isDenied() {
        withService(service -> {
            assertThatThrownBy(service::listOwned).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(service::ensureDefault).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.find(WORKSPACE)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.create(WorkspaceModel.builder().name("W").build()))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void everyMethod_missingWorkspaceOwnerGate_isDenied() {
        withService(service -> {
            // Local workspace capabilities alone must not open the layer: the app-wide gate is required.
            authenticate(OWNER, LocalPermissions.Workspace.ALL);

            assertThatThrownBy(service::listOwned).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(service::ensureDefault).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.find(WORKSPACE)).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void find_workspaceOwnedByAnotherUser_isDenied() {
        withService(service -> {
            authenticate(STRANGER, Set.of(Permissions.Workspace.OWNER));

            assertThatThrownBy(() -> service.find(WORKSPACE)).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void resourcelessMethods_needOnlyTheGate() {
        // create/listOwned/ensureDefault address no existing resource, so they use the flat check.
        withService(service -> {
            authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));

            assertThatCode(service::listOwned).doesNotThrowAnyException();
            assertThatCode(service::ensureDefault).doesNotThrowAnyException();
        });
    }

    @Test
    void find_ownerHoldingNoLocalCapabilities_isAllowed() {
        // Ownership overrides the declared workspace:read capability, exactly as it does for contents.
        withService(service -> {
            authenticate(OWNER, Set.of(Permissions.Workspace.OWNER));

            assertThatCode(() -> service.find(WORKSPACE)).doesNotThrowAnyException();
        });
    }

    private void withService(java.util.function.Consumer<WorkspaceService> body) {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            body.accept(context.getBean(WorkspaceService.class));
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

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        AuthorityChecker authorityChecker() {
            // Everything resolves to a workspace owned by OWNER, so the guards are what decide.
            WorkspaceScopeResolver resolver =
                    (resourceId, permission) -> Optional.of(new WorkspaceScope(WORKSPACE, OWNER));
            return new AuthorityChecker(resolver);
        }

        @Bean
        WorkspaceService workspaceService(WorkspaceRepository repository, WorkspaceMapper mapper,
                                          AuthorityChecker authorityChecker) {
            return new WorkspaceServiceImpl(
                    Mockito.mock(UniqueIdService.class), repository, mapper,
                    new WorkspaceProperties(new MockEnvironment()), authorityChecker,
                    Mockito.mock(vg.rg.repository.WorkspaceSelectionRepository.class),
                    // No contributors: this slice is about which callers the guards admit, not removal.
                    List.of());
        }

        @Bean
        WorkspaceRepository workspaceRepository() {
            var repository = Mockito.mock(WorkspaceRepository.class);
            Mockito.when(repository.findByOwnerUniqueIdOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of());
            Mockito.when(repository.findByDefaultForOwner(any()))
                    .thenReturn(Optional.of(WorkspaceEntity.builder()
                            .uniqueId(WORKSPACE.getLongValue())
                            .ownerUniqueId(OWNER)
                            .defaultForOwner(OWNER)
                            .build()));
            Mockito.when(repository.findById(any(UniqueId.class))).thenReturn(Optional.empty());
            return repository;
        }

        @Bean
        WorkspaceMapper workspaceMapper() {
            var mapper = Mockito.mock(WorkspaceMapper.class);
            Mockito.when(mapper.toModel(any()))
                    .thenReturn(WorkspaceModel.builder().defaultWorkspace(true).build());
            return mapper;
        }
    }
}
