package vg.rg.frontend.vaadin.component.datetime;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import vg.rg.frontend.vaadin.service.LocalizationService;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Drafts remain private until Save; Cancel, Escape and dismissal discard them. */
class TemporalEditDialog<T extends Comparable<? super T>> extends Dialog implements LocaleChangeObserver {
    private final LocalizationService localization;
    private final String labelKey;
    private final TemporalPicker<T> start;
    private final TemporalPicker<T> end;
    private final Consumer<TemporalRange<T>> onSave;
    private final boolean creating;
    private DateDisplayOptions options;
    private final Button save = new Button();
    private final Button cancel = new Button();
    private final Paragraph guidance = new Paragraph();
    private final Paragraph error = new Paragraph();
    private Locale locale;
    private String errorKey;

    TemporalEditDialog(LocalizationService localization, String labelKey, Supplier<TemporalPicker<T>> pickers,
                       boolean range, TemporalRange<T> initial, Locale locale, DateDisplayOptions options, Consumer<TemporalRange<T>> onSave) {
        this.localization = localization;
        this.labelKey = labelKey;
        this.locale = locale;
        this.options = options;
        this.onSave = onSave;
        creating = initial == null;
        start = pickers.get();
        end = range ? pickers.get() : null;
        start.value(initial == null ? null : initial.getStart());
        if (end != null) end.value(initial == null ? null : initial.getEnd());
        setWidth("min(36rem, calc(100vw - 2rem))");
        setCloseOnOutsideClick(false);
        var form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.add(start.component());
        if (end != null) form.add(end.component());
        error.getElement().setAttribute("role", "alert");
        error.addClassName("temporal-error");
        error.setVisible(false);
        add(guidance, form, error);
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> save());
        cancel.addClickListener(event -> close());
        getFooter().add(cancel, save);
        renderTranslations();
    }

    void setDisplayOptions(DateDisplayOptions options) {
        this.options = options;
        renderTranslations();
    }

    void save() {
        if (!isOpened()) return;
        if (start.value() == null || (end != null && end.value() == null)) {
            showError("dates.required");
            return;
        }
        if (start.invalid() || (end != null && end.invalid())) {
            showError("dates.invalid");
            return;
        }
        if (end != null && start.value().compareTo(end.value()) > 0) {
            showError("dates.range-invalid");
            return;
        }
        onSave.accept(TemporalRange.<T>builder().start(start.value())
                .end(end == null ? start.value() : end.value()).build());
        close();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        locale = event.getLocale();
        renderTranslations();
    }

    private void renderTranslations() {
        setHeaderTitle(text(creating ? "dates.create" : "dates.edit") + " · " + text(labelKey));
        start.localize(localization, locale, text(end == null ? labelKey : "dates.start"), options);
        if (end != null) end.localize(localization, locale, text("dates.end"), options);
        guidance.setText(text("dates.required-guidance"));
        save.setText(text("dates.save"));
        cancel.setText(text("dates.cancel"));
        if (errorKey != null) error.setText(text(errorKey));
    }

    private void showError(String key) {
        errorKey = key;
        error.setText(text(key));
        error.setVisible(true);
    }

    private String text(String key) { return localization.getTranslation(key, locale); }
}
