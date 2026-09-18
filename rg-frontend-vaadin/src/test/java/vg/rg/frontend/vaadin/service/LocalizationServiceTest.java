package vg.rg.frontend.vaadin.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;
import vg.rg.frontend.vaadin.component.datetime.DateDisplayOptions;
import vg.rg.frontend.vaadin.component.datetime.DateTimes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationServiceTest {

    private final LocalizationService service = new LocalizationService(messageSource());

    @Test
    void getProvidedLocales_defaultConfiguration_returnsOnlyUkrainianAndEnglish() {
        assertThat(service.getProvidedLocales()).containsExactly(Locale.forLanguageTag("uk-UA"), Locale.ENGLISH);
    }

    @Test
    void getDefaultLocale_defaultConfiguration_returnsUkrainian() {
        assertThat(service.getDefaultLocale()).isEqualTo(Locale.forLanguageTag("uk-UA"));
    }

    @Test
    void normalizeLocale_nullLocale_returnsUkrainian() {
        assertThat(service.normalizeLocale(null)).isEqualTo(Locale.forLanguageTag("uk-UA"));
    }

    @Test
    void normalizeLocale_unsupportedLocale_returnsUkrainian() {
        assertThat(service.normalizeLocale(Locale.FRANCE)).isEqualTo(Locale.forLanguageTag("uk-UA"));
    }

    @Test
    void normalizeLocale_supportedEnglish_returnsEnglish() {
        assertThat(service.normalizeLocale(Locale.ENGLISH)).isEqualTo(Locale.ENGLISH);
    }

    /**
     * Each locale resolves from its own bundle.
     *
     * <p>Compared against what the bundles actually contain rather than against literal text. Two
     * earlier tests pinned the product name here and broke the moment it was changed — a rebrand says
     * nothing about locale resolution, which is the thing under test.
     */
    @Test
    void getTranslation_resolvesEachLocaleFromItsOwnBundle() throws IOException {
        var ukrainian = bundleValue("/messages.properties", "project.name");
        var english = bundleValue("/messages_en.properties", "project.name");
        // Were the two ever identical, the assertions below could pass without resolving anything.
        assertThat(ukrainian).isNotEqualTo(english);

        assertThat(service.getTranslation("project.name", Locale.forLanguageTag("uk-UA")))
                .isEqualTo(ukrainian);
        assertThat(service.getTranslation("project.name", Locale.ENGLISH)).isEqualTo(english);
    }

    @Test
    void getTranslation_placeholderArgument_formatsPlaceholder() {
        assertThat(service.getTranslation("test.placeholder", Locale.ENGLISH, 3)).contains("3");
    }

    @Test
    void getTranslation_missingEnglishKey_returnsUkrainianTranslation() {
        var source = new StaticMessageSource();
        source.addMessage("test.ukrainian-only", Locale.forLanguageTag("uk-UA"), "Український резерв");
        var fallbackService = new LocalizationService(source);

        assertThat(fallbackService.getTranslation("test.ukrainian-only", Locale.ENGLISH))
                .isEqualTo("Український резерв");
    }

    @Test
    void getTranslation_missingEverywhere_returnsSafeUkrainianFallback() {
        var source = new StaticMessageSource();
        source.addMessage("i18n.missing", Locale.forLanguageTag("uk-UA"), "Переклад недоступний");
        var fallbackService = new LocalizationService(source);

        assertThat(fallbackService.getTranslation("missing.everywhere", Locale.ENGLISH))
                .isEqualTo("Переклад недоступний")
                .isNotBlank()
                .isNotEqualTo("missing.everywhere");
    }

    @Test
    void formatEventDateTime_nullInstant_returnsEmpty() {
        assertThat(service.formatEventDateTime(null,
                DateDisplayOptions.builder().showShortDayName(true).build())).isEmpty();
    }

    /**
     * Bridges to the shared {@code DateTimes} formatter, reading the instant as UTC and using the current
     * locale (uk-UA outside a Vaadin session). Includes the time, and the short weekday requested by the
     * options — so the caption and the temporal pickers share one presentation.
     */
    @Test
    void formatEventDateTime_delegatesToDateTimesWithUtcAndCurrentLocale() {
        var options = DateDisplayOptions.builder().showShortDayName(true).build();
        var instant = Instant.parse("2026-09-13T09:00:00Z");

        var formatted = service.formatEventDateTime(instant, options);

        var expected = DateTimes.format(DateTimes.toLocal(instant, ZoneOffset.UTC),
                Locale.forLanguageTag("uk-UA"), options);
        assertThat(formatted).isEqualTo(expected);
        // The datetime form carries the time after the date separator, distinguishing it from a date-only
        // rendering.
        assertThat(formatted).contains("09:00");
    }

    private static String bundleValue(String resource, String key) throws IOException {
        var properties = new Properties();
        try (var stream = LocalizationServiceTest.class.getResourceAsStream(resource)) {
            properties.load(new InputStreamReader(
                    Objects.requireNonNull(stream, resource), StandardCharsets.UTF_8));
        }
        return Objects.requireNonNull(properties.getProperty(key), key);
    }

    private static ResourceBundleMessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
