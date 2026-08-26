package vg.rg.frontend.vaadin.security;

import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.Permissions;

import java.util.List;
import java.util.Objects;

@Service
public class ApplicationSecurityContextService {

    private final AuthenticationEventPublisher authenticationEventPublisher;

    public ApplicationSecurityContextService(AuthenticationEventPublisher authenticationEventPublisher) {
        this.authenticationEventPublisher = authenticationEventPublisher;
    }

    public void authenticate(AuthenticatedUserPrincipal principal) {
        try {
            Objects.requireNonNull(principal);
            var authorities = principal.userUniqueId() == null
                    ? List.<SimpleGrantedAuthority>of()
                    : Permissions.recognized(principal.permissions()).stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            persist(context);
            SecurityContextHolder.setContext(context);
            authenticationEventPublisher.publishAuthenticationSuccess(authentication);
        } catch (RuntimeException exception) {
            clear();
            throw exception;
        }
    }

    public void clear() {
        SecurityContextHolder.clearContext();
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession != null && vaadinSession.getSession() != null) {
            vaadinSession.getSession().removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            return;
        }
        var request = VaadinServletRequest.getCurrent();
        if (request != null) {
            request.getHttpServletRequest().getSession().removeAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    private void persist(SecurityContext context) {
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession != null && vaadinSession.getSession() != null) {
            vaadinSession.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return;
        }
        var request = VaadinServletRequest.getCurrent();
        if (request != null) {
            request.getHttpServletRequest().getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }
}
