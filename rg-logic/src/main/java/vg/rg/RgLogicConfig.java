package vg.rg;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import vg.rg.config.EncryptionProperties;
import vg.rg.config.GeoProperties;
import vg.rg.config.WorkspaceProperties;
import vg.rg.config.security.IdentityAuthorizationLimitsProperties;
import vg.rg.config.security.SecureAuthorizationLimitsProperties;

import java.time.Clock;

/**
 * This module's Spring configuration.
 *
 * <p>Method security is enabled <strong>here</strong>, in the module that declares the
 * {@code @PreAuthorize} guards, rather than in each consuming application. A guard that is never
 * activated is not a weaker guard — it is no guard at all, and nothing fails: the annotation is simply
 * ignored, every call succeeds, and any test asserting a denial passes vacuously. Leaving that switch to
 * whoever wires up the app makes silent, total loss of authorization one forgotten annotation away.
 *
 * <p>Deliberately without {@code proxyTargetClass}: the guarded services are package-private classes
 * behind public interfaces, so interface proxies suffice, and matching production's proxying strategy is
 * what makes the functional tests representative.
 */
@Configuration
@EnableMethodSecurity
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditorAware")
@EnableJpaRepositories
@EntityScan
@EnableConfigurationProperties({
        EncryptionProperties.class,
        GeoProperties.class,
        WorkspaceProperties.class,
        SecureAuthorizationLimitsProperties.class,
        IdentityAuthorizationLimitsProperties.class
})
public class RgLogicConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
