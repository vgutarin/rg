package vg.rg.frontend.vaadin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthenticationEventsService {


    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        log.info("Application authentication succeeded");
    }

}
