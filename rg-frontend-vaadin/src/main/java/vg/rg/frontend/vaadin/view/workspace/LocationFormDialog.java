package vg.rg.frontend.vaadin.view.workspace;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import lombok.extern.slf4j.Slf4j;
import vg.rg.frontend.vaadin.component.button.RGButtonTheme;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.geo.LocationModel;
import vg.rg.service.workspace.WorkspaceLocationService;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Edit form for an existing location: carries the location's id, version, and coordinates, and updates
 * name/description. (Adding is handled inline in the locations views, not via this dialog.) Free-text
 * fields show localized anti-personal-data guidance (FR-018). Gated by {@code location:update} at the
 * service boundary; a stale edit surfaces the localized optimistic-lock "reload and retry" message
 * (FR-019).
 *
 * <p>The dialog holds the update operation rather than the service, so the form never learns which
 * workspace it is editing in — the location's own identifier resolves to that at the authority
 * boundary, and the scope is not editable content.
 */
@Slf4j
public class LocationFormDialog extends Dialog {


    private final LocalizationService localization;
    private final Consumer<LocationModel> updater;
    private final Runnable onSaved;

    private final UniqueIdRef existing;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String googlePlaceId;

    private final TextField name = new TextField();
    private final TextArea description = new TextArea();

    /** Minimal identity carried for edit mode (id + optimistic-lock version). */
    private record UniqueIdRef(vg.unique.id.model.UniqueId id, int version) {
    }

    private LocationFormDialog(LocalizationService localization,
                              Consumer<LocationModel> updater,
                              UniqueIdRef existing,
                              BigDecimal latitude,
                              BigDecimal longitude,
                              String googlePlaceId,
                              String initialName,
                              String initialDescription,
                              Runnable onSaved) {
        this.localization = localization;
        this.updater = updater;
        this.existing = existing;
        this.latitude = latitude;
        this.longitude = longitude;
        this.googlePlaceId = googlePlaceId;
        this.onSaved = onSaved;

        setHeaderTitle(localization.i18n("location.form.edit.title"));

        name.setLabel(localization.i18n("location.field.name"));
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();
        name.setValue(initialName == null ? "" : initialName);
        description.setLabel(localization.i18n("location.field.description"));
        description.setWidthFull();
        description.setValue(initialDescription == null ? "" : initialDescription);

        var guidance = new Paragraph(localization.i18n("location.pii-guidance"));
        var body = new VerticalLayout(guidance, name, description);
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();
        add(body);

        var cancel = new Button(localization.i18n("location.cancel"), event -> close());
        cancel.addThemeName(RGButtonTheme.BORDERED);
        var save = new Button(localization.i18n("location.save"), event -> save());
        save.addThemeName(RGButtonTheme.BORDERED);
        getFooter().add(cancel, save);
    }

    /**
     * Edits a location inside a workspace. The workspace is not passed: the location's own identifier
     * resolves to it at the authority boundary.
     */
    public static LocationFormDialog forEdit(LocalizationService localization,
                                             WorkspaceLocationService locationService,
                                             LocationModel model,
                                             Runnable onSaved) {
        return new LocationFormDialog(localization, locationService::update,
                new UniqueIdRef(model.getUniqueId(), model.getVersion()),
                model.getLatitude(), model.getLongitude(), model.getGooglePlaceId(),
                model.getName(), model.getDescription(), onSaved);
    }

    void save() {
        if (name.getValue() == null || name.getValue().isBlank()) {
            name.setInvalid(true);
            name.setErrorMessage(localization.i18n("validation.location.name-required"));
            return;
        }
        name.setInvalid(false);

        var model = LocationModel.builder()
                .uniqueId(existing.id())
                .version(existing.version())
                .name(name.getValue().trim())
                .description(blankToNull(description.getValue()))
                .latitude(latitude)
                .longitude(longitude)
                .googlePlaceId(googlePlaceId)
                .build();

        try {
            updater.accept(model);
            close();
            Notification.show(localization.i18n("location.created"));
            onSaved.run();
        } catch (RuntimeException exception) {
            // Log the concrete failure (class + message names e.g. the unreachable service/URL) with the
            // full cause chain, so a swallowed save error is diagnosable beyond the localized notification.
            log.error("Failed to update location \"{}\": {}",
                    name.getValue(), exception.toString(), exception);
            Notification.show(messageFor(exception));
        }
    }

    /**
     * Localized message for a failed save. Invalid input maps to the coordinate-validation message;
     * everything else (including {@code ObjectOptimisticLockingFailureException} → the localized
     * "reload and retry", and access denial) maps through the exception message keys.
     */
    String messageFor(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return localization.i18n("validation.location.coordinates-invalid");
        }
        return localization.i18n(exception);
    }

    TextField nameField() {
        return name;
    }

    TextArea descriptionField() {
        return description;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
