package vg.rg.frontend.vaadin.service;

import com.vaadin.flow.server.SessionInitEvent;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.config.VaadinLocaleServiceInitListener;

import java.util.Locale;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocaleSessionIntegrationTest {

    @Mock SessionInitEvent event;
    @Mock VaadinSession session;

    @Test
    void sessionInit_newSession_setsUkrainianLocale() throws Exception {
        when(event.getSession()).thenReturn(session);

        new VaadinLocaleServiceInitListener().sessionInit(event);

        verify(session).setLocale(Locale.forLanguageTag("uk-UA"));
    }
}
