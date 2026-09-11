package vg.rg.frontend.vaadin.component.datetime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalPickerLayoutTest {

    @Test
    void dateAndTimeInputsStayOnOneLine() throws IOException {
        var stylesheet = Files.readString(repositoryRoot()
                .resolve("rg-frontend-vaadin/src/main/resources/META-INF/resources/styles.css"));

        assertThat(stylesheet).contains(".temporal-picker::part(input-fields) { flex-flow: row nowrap; }");
        assertThat(stylesheet).contains(".temporal-picker > vaadin-date-picker, .temporal-picker > vaadin-time-picker"
                + " { min-width: 0; width: auto; }");
    }

    private static Path repositoryRoot() {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return candidate;
    }
}
