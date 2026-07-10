package vg.template.rest.v1;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import vg.template.model.TemplateModel;
import vg.template.service.TemplateService;

import java.util.Collection;

@HttpExchange("model")
public interface TemplateServiceApiRestClient extends TemplateService {

    @Override
    @PostExchange("create")
    TemplateModel create(@RequestBody TemplateModel templateModel);

    @Override
    @PutExchange("update")
    TemplateModel update(@RequestBody TemplateModel templateModel);

    @Override
    @GetExchange("all")
    Collection<TemplateModel> getAll();
}
