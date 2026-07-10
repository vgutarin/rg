package vg.template;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories
@EntityScan
public class TemplateLogicConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
