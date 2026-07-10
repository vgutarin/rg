package vg.template.service;


import vg.template.model.TemplateModel;

import java.util.Collection;

public interface TemplateService {
    TemplateModel create(TemplateModel templateModel);
    TemplateModel update(TemplateModel templateModel);
    Collection<TemplateModel> getAll();
}
