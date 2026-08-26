package vg.rg.frontend.vaadin.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.rg.entity.LocationEntity;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.LocationModel;
import vg.rg.service.LocationService;
import vg.unique.id.model.UniqueId;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationFormDialogTest {

    @Mock LocalizationService localization;
    @Mock LocationService locationService;

    @BeforeEach
    void echoLabels() {
        lenient().when(localization.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LocationFormDialog editDialog() {
        var model = LocationModel.builder()
                .uniqueId(new UniqueId(1L)).version(3)
                .name("Central Cafe").description("By the fountain")
                .latitude(BigDecimal.valueOf(50.0)).longitude(BigDecimal.valueOf(30.0))
                .build();
        return LocationFormDialog.forEdit(localization, locationService, model, () -> { });
    }

    @Test
    void forEdit_prefillsNameAndDescription() {
        var dialog = editDialog();

        assertThat(dialog.nameField().getValue()).isEqualTo("Central Cafe");
        assertThat(dialog.descriptionField().getValue()).isEqualTo("By the fountain");
    }

    @Test
    void save_blankName_marksFieldInvalidAndDoesNotPersist() {
        var dialog = editDialog();
        dialog.nameField().setValue("   ");

        dialog.save();

        assertThat(dialog.nameField().isInvalid()).isTrue();
        verify(locationService, never()).update(any());
    }

    @Test
    void messageFor_optimisticLock_usesLocalizedExceptionMessage() {
        when(localization.i18n(any(RuntimeException.class))).thenReturn("reload-and-retry");
        var dialog = editDialog();

        var message = dialog.messageFor(
                new ObjectOptimisticLockingFailureException(LocationEntity.class, new UniqueId(1L)));

        assertThat(message).isEqualTo("reload-and-retry");
    }

    @Test
    void messageFor_invalidInput_usesCoordinateValidationMessage() {
        var dialog = editDialog();

        var message = dialog.messageFor(new IllegalArgumentException("bad"));

        assertThat(message).isEqualTo("validation.location.coordinates-invalid");
    }
}
