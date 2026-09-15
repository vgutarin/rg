package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.dependency.JsModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalDateTimePickerTest {

    @Test
    void directDateTimePickersOwnTheGroupedTimeModule() {
        assertThat(TemporalPicker.dateTime().component()).isInstanceOf(TemporalDateTimePicker.class);
        assertThat(TemporalDateTimePicker.class.getAnnotation(JsModule.class).value())
                .isEqualTo("./ts/datetime/temporal-time-picker.ts");
        assertThat(TemporalEditor.class.getAnnotationsByType(JsModule.class))
                .extracting(JsModule::value)
                .doesNotContain("./ts/datetime/temporal-time-picker.ts");
    }
}
