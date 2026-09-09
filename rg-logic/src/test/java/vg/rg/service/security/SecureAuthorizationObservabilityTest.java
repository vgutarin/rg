package vg.rg.service.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApi;
import vg.rg.config.security.IdentityAuthorizationLimitsProperties;
import vg.rg.config.security.SecureAuthorizationLimitsProperties;
import vg.rg.entity.workspace.WorkspaceEntity;
import vg.rg.entity.workspace.WorkspaceLocationEntity;
import vg.rg.entity.workspace.WorkspaceSelectionEntity;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.security.AuthorizationOutcome;
import vg.rg.model.security.Permissions;
import vg.rg.model.security.TelegramInitDataRequest;
import vg.unique.id.model.UniqueId;

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

            var limits = new SecureAuthorizationLimitsProperties(null);
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
                        new SecureAuthorizationLimitsProperties(null)));

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
        var limits = new IdentityAuthorizationLimitsProperties(null, null);
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
     * No workspace entity carries personal data <em>in a plainly named field</em>. The identity fields
     * these types do carry are the abstract {@code UniqueId} — an owner for access control, an author
     * and last editor for auditing — and are never mapped to a natural person.
     *
     * <p>{@code WorkspaceParticipantEntity} is deliberately absent from this list, and that is not an
     * oversight. It <strong>does</strong> carry personal data, under the narrow allowance amended into
     * Principle I, so including it here would make this assertion pass for the wrong reason: the field
     * is called {@code descriptor}, which no naming rule would ever catch. What actually has to hold for
     * that entity is a different property, asserted in
     * {@link #participantContactData_hasNoPlaintextRouteOutOfTheEntity()}.
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

    /**
     * The participant entity's counterpart to the naming check above, which cannot help here.
     *
     * <p>Two structural facts, and together they are the reason the personal data on this entity is
     * permitted at all. First, <strong>the entity declares no {@code String} field</strong>: there is no
     * plaintext text column for anything to leak through, so the contact data has exactly one route in
     * and out. Second, that route is {@code descriptor}, and it is annotated with the encrypting
     * converter — so removing the annotation, or pointing it at a converter that does not encrypt, fails
     * here rather than in production.
     */
    @Test
    void participantContactData_hasNoPlaintextRouteOutOfTheEntity() throws NoSuchFieldException {
        var stringFields = java.util.Arrays.stream(
                        vg.rg.entity.workspace.WorkspaceParticipantEntity.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(stringFields)
                .as("a plaintext text column on the participant entity")
                .isEmpty();

        var descriptor = vg.rg.entity.workspace.WorkspaceParticipantEntity.class.getDeclaredField("descriptor");
        assertThat(descriptor.getType()).isEqualTo(vg.rg.model.workspace.ParticipantDescriptor.class);
        assertThat(descriptor.getAnnotation(jakarta.persistence.Convert.class)).isNotNull();
        assertThat(descriptor.getAnnotation(jakarta.persistence.Convert.class).converter())
                .isEqualTo(vg.rg.entity.workspace.ParticipantDescriptorConverter.class);
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
