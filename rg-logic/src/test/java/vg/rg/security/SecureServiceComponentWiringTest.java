package vg.rg.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.security.dev.DevSecureAuthorizationFacade;
import vg.rg.security.dev.DevSecureServiceProperties;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.service.ProtectedActionService;
import vg.rg.service.ProtectedActionServiceImpl;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class SecureServiceComponentWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecureComponents.class, SupportConfiguration.class)
            .withPropertyValues("rg.secure-service.bot-token=synthetic-token");

    @Test
    void applicationContext_developmentFacadeEnabled_loadsOnlyDevelopmentFacade() {
        runner.withPropertyValues("rg.secure-service.enabled=true")
                .run(SecureServiceComponentWiringTest::assertDevelopmentComponents);
    }

    @Test
    void applicationContext_developmentFacadeUnspecified_loadsIdentityFacade() {
        runner.withUserConfiguration(IdentityApiConfiguration.class)
                .run(SecureServiceComponentWiringTest::assertIdentityComponents);
    }

    @Test
    void applicationContext_developmentFacadeDisabled_loadsIdentityFacade() {
        runner.withUserConfiguration(IdentityApiConfiguration.class)
                .withPropertyValues("rg.secure-service.enabled=false")
                .run(SecureServiceComponentWiringTest::assertIdentityComponents);
    }

    private static void assertIdentityComponents(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(SecureAuthorizationFacade.class);
        assertThat(context.getBean(SecureAuthorizationFacade.class))
                .isInstanceOf(IdentitySecureAuthorizationFacade.class);
        assertThat(context).doesNotHaveBean(DevSecureAuthorizationFacade.class);
        assertSharedComponents(context);
    }

    @Test
    void applicationContext_identityMode_loadsSharedAuthorizationServices() {
        runner.withUserConfiguration(IdentityApiConfiguration.class)
                .withPropertyValues("rg.secure-service.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertSharedComponents(context);
                });
    }

    @Test
    void applicationContext_noFacade_failsStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(NoFacadeComponents.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("SecureAuthorizationFacade");
                });
    }

    @Test
    void applicationContext_multipleFacades_failsStartup() {
        runner.withPropertyValues("rg.secure-service.enabled=true")
                .withUserConfiguration(ExtraFacadeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("SecureAuthorizationFacade");
                });
    }

    private static void assertDevelopmentComponents(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(SecureAuthorizationFacade.class);
        assertThat(context.getBean(SecureAuthorizationFacade.class))
                .isInstanceOf(DevSecureAuthorizationFacade.class);
        assertThat(context).doesNotHaveBean(IdentitySecureAuthorizationFacade.class);
        assertSharedComponents(context);
    }

    private static void assertSharedComponents(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(AuthorizationApplicationService.class);
        assertThat(context).hasSingleBean(SecureAuthorizationLimitsProperties.class);
        assertThat(context).hasSingleBean(TelegramAuthorizationRequestValidator.class);
        assertThat(context).hasSingleBean(AuthorityChecker.class);
        assertThat(context).hasSingleBean(ProtectedActionService.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DevSecureServiceProperties.class)
    @Import({
            DevSecureAuthorizationFacade.class,
            IdentitySecureAuthorizationFacade.class,
            IdentityAuthorizationLimitsProperties.class,
            IdentityAuthorizationResponseValidator.class,
            SecureAuthorizationLimitsProperties.class,
            TelegramAuthorizationRequestValidator.class,
            AuthorizationApplicationService.class,
            AuthorityChecker.class,
            ProtectedActionServiceImpl.class
    })
    static class SecureComponents {
    }

    @Configuration(proxyBeanMethods = false)
    static class SupportConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class IdentityApiConfiguration {
        @Bean IdentityApplicationApi identityApplicationApi() {
            return org.mockito.Mockito.mock(IdentityApplicationApi.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            SecureAuthorizationLimitsProperties.class,
            TelegramAuthorizationRequestValidator.class,
            AuthorizationApplicationService.class
    })
    static class NoFacadeComponents {
    }

    @Configuration(proxyBeanMethods = false)
    static class ExtraFacadeConfiguration {
        @Bean SecureAuthorizationFacade extraFacade() {
            return org.mockito.Mockito.mock(SecureAuthorizationFacade.class);
        }
    }
}
