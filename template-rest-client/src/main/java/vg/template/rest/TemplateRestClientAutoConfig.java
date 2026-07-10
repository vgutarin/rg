package vg.template.rest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(TemplateRestClient.class)
public class TemplateRestClientAutoConfig {
}
