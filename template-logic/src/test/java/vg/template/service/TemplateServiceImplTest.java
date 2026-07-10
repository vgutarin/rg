package vg.template.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.template.entity.TemplateEntity;
import vg.template.mapper.TemplateMapper;
import vg.template.model.TemplateModel;
import vg.template.repository.TemplateRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    TemplateRepository repository;
    @Mock
    TemplateMapper mapper;

    @InjectMocks
    TemplateServiceImpl service;

    @Test
    void create() {

        var modelToSave = TemplateModel.builder().build();
        var modelSaved = model(1L);

        var entityToSave = TemplateEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);

        when(mapper.toEntity(modelToSave)).thenReturn(entityToSave);
        when(repository.saveWithNewUniqueId(entityToSave, uniqueIdService)).thenReturn(entitySaved);
        when(mapper.toModel(entitySaved)).thenReturn(modelSaved);

        assertThat(
                service.create(modelToSave)
        ).isSameAs(
                modelSaved
        );
    }

    @Test
    void update() {

        var modelId = nextUniqueId();
        var updatedName = nextString();

        var model = TemplateModel.builder().uniqueId(modelId).name(updatedName).build();

        var entityId = modelId.value();
        var entity = TemplateEntity.builder().uniqueId(entityId).build();
        var entitySaved = TemplateEntity.builder()
                .uniqueId(entityId)
                .name(updatedName)
                .description(nextString())
                .build();

        when(repository.findById(modelId)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entitySaved);

        assertThat(
                service.update(model)
        ).isSameAs(
                model
        );

        verify(mapper).updateEntity(entity, model);
        verify(mapper).updateModel(model, entitySaved);
    }

    @Test
    void updateThrows_WhenEntityIsNotFound() {

        var model = model(nextLong());

        when(repository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.update(model)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAll() {

        var entity1 = entity(1);
        var entity2 = entity(2);
        var entity3 = entity(3);

        var model1 = model(1);
        var model2 = model(2);
        var model3 = model(3);

        when(repository.findAll()).thenReturn(List.of(entity1, entity2, entity3));

        when(mapper.toModel(entity1)).thenReturn(model1);
        when(mapper.toModel(entity2)).thenReturn(model2);
        when(mapper.toModel(entity3)).thenReturn(model3);

        assertThat(
                service.getAll()
        ).isEqualTo(
                List.of(model1, model2, model3)
        );
    }

    private static TemplateModel model(long id) {
        return TemplateModel.builder().uniqueId(new UniqueId(id)).build();
    }

    private static TemplateEntity entity(long id) {
        return TemplateEntity.builder().uniqueId(id).build();
    }
}