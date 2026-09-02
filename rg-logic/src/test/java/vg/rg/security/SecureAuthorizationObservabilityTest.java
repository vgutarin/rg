package vg.rg.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApi;
import vg.unique.id.model.UniqueId;
import vg.rg.security.identity.IdentityAuthorizationLimitsProperties;
import vg.rg.security.identity.IdentityAuthorizationResponseValidator;
import vg.rg.security.identity.IdentitySecureAuthorizationFacade;
import vg.rg.entity.WorkspaceEntity;
import vg.rg.entity.WorkspaceLocationEntity;
import vg.rg.entity.WorkspaceSelectionEntity;
import vg.rg.security.model.AuthenticatedUserPrincipal;
import vg.rg.security.model.AuthenticationFlow;
import vg.rg.security.model.LocalPermissions;
import vg.rg.security.model.Permissions;
import vg.rg.security.model.AuthorizationOutcome;
import vg.rg.security.model.TelegramInitDataRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecureAuthorizationObservabilityTest {

    @Test
    void redeem_sensitiveRequest_logsOnlyBoundedIdentifierAndOutcome() {
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            SecureAuthorizationFacade facade = request -> AuthorizationOutcome.denied();
            var sensitiveMarker = "synthetic-sensitive-telegram-payload";

            var limits = new SecureAuthorizationLimitsProperties(new MockEnvironment());
            new AuthorizationApplicationService(
                    facade, new TelegramAuthorizationRequestValidator(limits))
                    .redeem(new TelegramInitDataRequest(sensitiveMarker));

            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anySatisfy(message -> assertThat(message)
                    .containsPattern("request [0-9a-f-]{36} outcome DENIED")
                    .doesNotContain(sensitiveMarker));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void redeem_allOperationalOutcomesAndExplicitRetry_logOnlyRequestIdsAndCodes() {
        var sensitivePayload = "synthetic-sensitive-telegram-payload";
        var sensitiveSubjectId = new UniqueId(918273645L);
        var sensitiveName = "Synthetic Sensitive Name";
        var sensitivePermission = "synthetic:permission";
        var outcomes = new java.util.ArrayDeque<>(List.of(
                AuthorizationOutcome.authorized(new AuthenticatedUserPrincipal(
                        sensitiveSubjectId, sensitiveName, Set.of(sensitivePermission), true,
                        AuthenticationFlow.TELEGRAM)),
                AuthorizationOutcome.denied(),
                AuthorizationOutcome.unavailable(Duration.ofSeconds(1)),
                AuthorizationOutcome.incompatible(),
                AuthorizationOutcome.authorized(new AuthenticatedUserPrincipal(
                        sensitiveSubjectId, sensitiveName, Set.of(Permissions.Reports.READ), true,
                        AuthenticationFlow.TELEGRAM))));
        SecureAuthorizationFacade facade = request -> outcomes.removeFirst();
        var service = new AuthorizationApplicationService(
                facade,
                new TelegramAuthorizationRequestValidator(
                        new SecureAuthorizationLimitsProperties(new MockEnvironment())));

        var messages = captureLogs(() -> {
            for (int attempt = 0; attempt < 5; attempt++) {
                service.redeem(new TelegramInitDataRequest(sensitivePayload));
            }
        });

        assertThat(messages).filteredOn(message -> message.contains("Secure authorization request"))
                .hasSize(5)
                .anyMatch(message -> message.endsWith("outcome AUTHORIZED"))
                .anyMatch(message -> message.endsWith("outcome DENIED"))
                .anyMatch(message -> message.endsWith("outcome UNAVAILABLE"))
                .anyMatch(message -> message.endsWith("outcome INCOMPATIBLE"));
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(
                sensitivePayload, sensitiveSubjectId.toString(), sensitiveName, sensitivePermission));
    }

    @Test
    void identityBoundary_nullSubjectDuplicatesAndUnavailable_warnWithoutExternalValues() {
        var sensitiveSubject = "synthetic-sensitive-subject";
        var sensitiveName = "Synthetic Sensitive Name";
        var sensitivePermission = "synthetic:permission";
        var sensitiveCredential = "synthetic-sensitive-credential";
        var sensitiveUpstreamDetail = "synthetic-sensitive-upstream-detail";
        var limits = new IdentityAuthorizationLimitsProperties(new MockEnvironment());
        var validator = new IdentityAuthorizationResponseValidator(limits);
        var api = mock(IdentityApplicationApi.class);
        when(api.authenticateTelegram(any()))
                .thenReturn(Optional.of(new IdentityApplicationUserPrincipal(
                        null, sensitiveName, Set.of(sensitivePermission), false)))
                .thenThrow(new IllegalStateException(sensitiveUpstreamDetail));
        var facade = new IdentitySecureAuthorizationFacade(api, validator);

        var messages = captureLogs(() -> {
            validator.validate(List.of(sensitivePermission, sensitivePermission));
            facade.redeemAuthorizationGrant(new TelegramInitDataRequest("auth_date=1&hash=x"));
            facade.redeemAuthorizationGrant(new TelegramInitDataRequest("auth_date=1&hash=x"));
        });

        assertThat(messages).anyMatch(message -> message.contains("duplicate permissions"));
        assertThat(messages).anyMatch(message -> message.contains("permissions without a subject"));
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(
                sensitiveSubject, sensitiveName, sensitivePermission, sensitiveCredential,
                sensitiveUpstreamDetail, "auth_date", "hash=x"));
    }

    /**
     * The workspace layer's own logging. Its warnings exist so an unexplained denial can be diagnosed,
     * which means they have to name the resource and the permission — and nothing else. A workspace name
     * or a location's content in a warning would leak user data into operations for no diagnostic gain.
     */
    @Test
    void workspaceScopeResolution_everyUnresolvablePath_warnsWithIdentifiersOnly() {
        var resource = new UniqueId(918273645L);
        var sensitiveContent = "Synthetic Sensitive Place Name";
        // Claims location capabilities and finds nothing, which is the path that has to name the
        // resource: the row is missing, or the permission names a different type than the identifier.
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
        // Diagnosable: every warning names the permission, and the lookup that failed names the resource.
        assertThat(warnings).allSatisfy(message ->
                assertThat(message).containsPattern("location:update|workspace:owner|workspace:update"));
        assertThat(warnings).anySatisfy(message -> assertThat(message)
                .contains(LocalPermissions.Location.UPDATE, resource.toString(), "resource lookup"));
        assertThat(warnings).allSatisfy(message ->
                assertThat(message).doesNotContain(sensitiveContent));
    }

    /**
     * No workspace entity carries personal data. The identity fields it does carry are the abstract
     * {@code UniqueId} — an owner for access control, an author and last editor for auditing — and are
     * never mapped to a natural person.
     */
    @Test
    void noWorkspaceEntityDeclaresAPersonalDataField() {
        var personalData = List.of("email", "phone", "firstname", "lastname", "fullname", "surname",
                "address", "birth", "gender", "avatar", "photo", "username", "telegram", "ipaddress");

        for (var entity : List.of(WorkspaceEntity.class, WorkspaceLocationEntity.class,
                WorkspaceSelectionEntity.class)) {
            var fields = java.util.Arrays.stream(entity.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName)
                    .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                    .toList();
            assertThat(fields).as("%s fields", entity.getSimpleName())
                    .allSatisfy(field -> assertThat(personalData)
                            .as("%s.%s looks like personal data", entity.getSimpleName(), field)
                            .noneMatch(field::contains));
        }
    }

    private java.util.List<String> captureLogs(Runnable action) {
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
