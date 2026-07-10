package vg.template.rest;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import vg.lib.rest.RestClientFactory;
import vg.template.rest.v1.TemplateServiceApiRestClient;

@ComponentScan
@Configuration
public class TemplateRestClient {

    //TODO autowire
    private final RestClientFactory restClientFactory = new RestClientFactory();

    @Bean
    TemplateServiceApiRestClient templateServiceApiRestClient(@Value("${vg.template.rest.service.base-url:http://localhost:8080}") String serviceBaseUrl) {
        return restClientFactory.createClient(serviceBaseUrl, TemplateServiceApiRestClient.class);
    }
}
