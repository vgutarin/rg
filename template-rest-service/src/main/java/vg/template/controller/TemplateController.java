package vg.template.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.template.model.TemplateModel;
import vg.template.service.TemplateService;
import vg.template.service.TemplateServiceImpl;

import java.util.Collection;

@RequiredArgsConstructor
@RestController
@RequestMapping("model")
public class TemplateController implements TemplateService {

    private final TemplateServiceImpl logic;

    @Override
    @PostMapping("create")
    public TemplateModel create(@RequestBody TemplateModel templateModel) {
        return logic.create(templateModel);
    }

    @Override
    @PutMapping("update")
    public TemplateModel update(@RequestBody TemplateModel templateModel) {
        return logic.update(templateModel);
    }

    @GetMapping("all")
    @Override
    public Collection<TemplateModel> getAll() {
        return logic.getAll();
    }
}
