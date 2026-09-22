package vg.rg.frontend.vaadin.view.home;

import vg.rg.frontend.vaadin.service.ApplicationSessionAttributes;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/** Server-session-only continuation for an explicitly consented Join Society action. */
public final class JoinSocietyIntent {
    private JoinSocietyIntent() { }

    public static void remember(UniqueId eventId) {
        ApplicationSessionAttributes.put(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID, eventId.getLongValue());
    }

    public static boolean isPending() {
        return ApplicationSessionAttributes.isPresent(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID);
    }

    public static Optional<UniqueId> consume() {
        return ApplicationSessionAttributes.consume(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID)
                .map(UniqueId::new);
    }
}
