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
import vg.rg.config.GeoProperties;
import vg.rg.mapper.LocationMapper;
import vg.rg.model.LocationModel;
import vg.rg.model.ProximityQuery;
import vg.rg.repository.LocationRepository;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class LocationServiceMethodSecurityTest {

    private static final ProximityQuery QUERY =
            new ProximityQuery(BigDecimal.valueOf(50.0), BigDecimal.valueOf(30.0), null);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findNearby_missingAuthentication_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(LocationService.class);

            assertThatThrownBy(() -> service.findNearby(QUERY))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void findNearby_missingViewPermission_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(LocationService.class);

            authenticate(Set.of(Permissions.Location.ADD));
            assertThatThrownBy(() -> service.findNearby(QUERY))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void findNearby_grantedViewPermission_isAllowed() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(LocationService.class);

            authenticate(Set.of(Permissions.Location.VIEW));
            assertThat(service.findNearby(QUERY)).isEmpty();
        }
    }

    @Test
    void create_missingAuthentication_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(LocationService.class);

            assertThatThrownBy(() -> service.create(LocationModel.builder().build()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void create_missingAddPermission_throwsAccessDeniedException() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var service = context.getBean(LocationService.class);

            authenticate(Set.of(Permissions.Location.VIEW));
            assertThatThrownBy(() -> service.create(LocationModel.builder().build()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    private void authenticate(Set<String> permissions) {
        var principal = new AuthenticatedUserPrincipal(
                new UniqueId(1234L), "Test User", permissions, true, AuthenticationFlow.TELEGRAM);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        AuthorityChecker authorityChecker() {
            return new AuthorityChecker();
        }

        @Bean
        LocationRepository locationRepository() {
            var repository = Mockito.mock(LocationRepository.class);
            Mockito.when(repository.findWithinBoundingBox(any(), any(), any(), any()))
                    .thenReturn(List.of());
            return repository;
        }

        @Bean
        LocationService locationService(AuthorityChecker authorityChecker, LocationRepository repository) {
            return new LocationServiceImpl(
                    Mockito.mock(UniqueIdService.class),
                    repository,
                    Mockito.mock(LocationMapper.class),
                    new GeoProperties(new MockEnvironment()),
                    authorityChecker);
        }
    }
}
