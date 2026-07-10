package vg.rg.frontend.vaadin.config;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.SessionInitEvent;
import com.vaadin.flow.server.SessionInitListener;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;
import vg.rg.frontend.vaadin.service.LocalizationService;

@Component
public class VaadinLocaleServiceInitListener implements VaadinServiceInitListener, SessionInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(this);
    }

    @Override
    public void sessionInit(SessionInitEvent event) {
        event.getSession().setLocale(LocalizationService.DEFAULT_LOCALE);
    }
}
