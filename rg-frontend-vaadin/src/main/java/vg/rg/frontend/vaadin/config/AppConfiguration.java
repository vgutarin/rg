package vg.rg.frontend.vaadin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * This module's Spring configuration. {@code @ConfigurationProperties} types are not component-scanned,
 * so every holder this module owns is registered here.
 */
@Configuration
@EnableConfigurationProperties(MapsClientProperties.class)
public class AppConfiguration {
}
