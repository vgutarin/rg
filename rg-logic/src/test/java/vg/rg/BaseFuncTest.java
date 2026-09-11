package vg.rg;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.Permissions;
import vg.test.containers.starters.Mysql8ContainerStarter;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Set;

@SpringBootTest
@ActiveProfiles({"test", "integration"})
@SpringBootApplication
public class BaseFuncTest implements Mysql8ContainerStarter {

    protected static void authenticate(UniqueId user) {
        authenticate(user, Set.of(Permissions.Workspace.OWNER));
    }

    protected static void authenticate(UniqueId user, Set<String> permissions) {
        var principal = AuthenticatedUserPrincipal.builder()
                .userUniqueId(user)
                .name("Test User")
                .permissions(permissions)
                .consentGiven(true)
                .authenticationFlow(AuthenticationFlow.TELEGRAM)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
