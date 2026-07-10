package vg.rg.frontend.vaadin.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationBundleTest {

    @Test
    void load_ukrainianAndEnglishBundles_returnsIdenticalKeys() throws IOException {
        var ukrainian = load("messages.properties");
        var english = load("messages_en.properties");

        assertThat(english.keySet()).isEqualTo(ukrainian.keySet());
    }

    @Test
    void load_ukrainianBundle_returnsNonblankValues() throws IOException {
        var ukrainian = load("messages.properties");

        assertThat(ukrainian).allSatisfy((key, value) -> assertThat(value.toString()).isNotBlank());
    }

    @Test
    void load_englishBundle_returnsNonblankValues() throws IOException {
        var english = load("messages_en.properties");

        assertThat(english).allSatisfy((key, value) -> assertThat(value.toString()).isNotBlank());
    }

    private Properties load(String name) throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as(name).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
