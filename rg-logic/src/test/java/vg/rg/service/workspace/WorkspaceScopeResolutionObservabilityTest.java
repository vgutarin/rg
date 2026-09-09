package vg.rg.service.workspace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vg.rg.model.security.LocalPermissions;
import vg.rg.model.security.Permissions;
import vg.rg.model.security.WorkspaceScope;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceScopeResolutionObservabilityTest {

    @Test
    void workspaceScopeResolution_everyUnresolvablePath_warnsWithIdentifiersOnly() {
        var resource = new UniqueId(918273645L);
        var sensitiveContent = "Synthetic Sensitive Place Name";
        WorkspaceScopeProvider findsNothing = new WorkspaceScopeProvider() {
            @Override
            public boolean supports(String permission) {
                return LocalPermissions.Location.contains(permission);
            }

            @Override
            public Optional<WorkspaceScope> findByResource(UniqueId resourceId) {
                return Optional.empty();
            }

            @Override
            public Optional<WorkspaceScope> findByContainer(UniqueId containerId) {
                return Optional.empty();
            }
        };
        var resolver = new WorkspaceScopeResolverImpl(List.of(findsNothing));

        var messages = captureLogs(() -> {
            resolver.resolve(null, LocalPermissions.Location.UPDATE);
            resolver.resolve(resource, Permissions.Workspace.OWNER);
            resolver.resolve(resource, LocalPermissions.Workspace.UPDATE);
            resolver.resolve(resource, LocalPermissions.Location.UPDATE);
        });

        var warnings = messages.stream()
                .filter(message -> message.startsWith("Workspace scope not resolvable"))
                .toList();
        assertThat(warnings).hasSize(4);
        assertThat(warnings).allSatisfy(message ->
                assertThat(message).containsPattern("location:update|workspace:owner|workspace:update"));
        assertThat(warnings).anySatisfy(message -> assertThat(message)
                .contains(LocalPermissions.Location.UPDATE, resource.toString(), "resource lookup"));
        assertThat(warnings).allSatisfy(message ->
                assertThat(message).doesNotContain(sensitiveContent));
    }

    private List<String> captureLogs(Runnable action) {
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
