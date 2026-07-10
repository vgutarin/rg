package vg.rg.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.rg.BaseFuncTest;
import vg.rg.model.TemplateModel;
import vg.rg.repository.TemplateRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

class TemplateServiceImplFuncTest extends BaseFuncTest {

    @Autowired
    private TemplateRepository repository;

    @Autowired
    private TemplateServiceImpl service;

    private String name;
    private String description;

    @BeforeEach
    void setUp() {
        name = nextString();
        description = nextString();
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void create_validModel_persistsModel() {
        var savedModel = service.create(buildModel());

        assertThat(
                savedModel.getName()
        ).isEqualTo(
                name
        );

        assertThat(
                savedModel.getDescription()
        ).isEqualTo(
                description
        );

        assertThat(
                savedModel.getCreatedAt()
        ).isNotNull();

        assertThat(
            savedModel.getUpdatedAt()
        ).isNotNull();

        assertThat(
                savedModel.getUniqueId()
        ).isNotNull();


        assertThat(
                savedModel.getVersion()
        ).isEqualTo(
                0
        );

        assertThat(service.getAll()).contains(savedModel);
    }

    @Test
    void update_existingModel_persistsChanges() {
        var savedModel = service.create(buildModel());

        var savedModelId = savedModel.getUniqueId();
        var newName = nextString();
        var newDescription = nextString();

        savedModel.setName(newName);
        savedModel.setDescription(newDescription);

        var updatedModel = service.update(savedModel);

        assertThat(updatedModel).isSameAs(savedModel);

        assertThat(
                updatedModel.getName()
        ).isEqualTo(
                newName
        );

        assertThat(
                updatedModel.getDescription()
        ).isEqualTo(
                newDescription
        );

        assertThat(
                updatedModel.getCreatedAt()
        ).isBefore(
            updatedModel.getUpdatedAt()
        );

        assertThat(
                updatedModel.getUniqueId()
        ).isEqualTo(savedModelId);

        assertThat(
                updatedModel.getVersion()
        ).isEqualTo(
                1
        );

        assertThat(service.getAll()).contains(updatedModel);
    }

    private TemplateModel buildModel() {
        return TemplateModel.builder()
                .uniqueId(null)
                .name(name)
                .description(description)
                .build();
    }
}
