package vg.rg.frontend.vaadin.view.home;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.geo.LocationAudienceModel;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.workspace.WorkspaceEventAudienceModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.rg.service.resource.ResourcePublicationService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandingViewTest {

    @Mock LocalizationService localization;
    @Mock AuthenticationContext authenticationContext;
    @Mock BeforeEnterEvent event;
    @Mock ResourcePublicationService publicationService;
    @Mock WorkspaceMembershipRequestService membershipRequestService;

    @Test
    void authenticatedUserSeesTheWelcomeMessage() {
        when(localization.i18n("home.welcome")).thenReturn("Welcome");
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal()));
        when(publicationService.browseWorkspaceEvents(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());

        var landing = landing();
        landing.beforeEnter(event);

        assertThat(landing.welcomeText()).isEqualTo("Welcome");
    }

    @Test
    void localeChangeRetranslatesTheWelcomeMessage() {
        when(localization.i18n(anyString())).thenReturn("First welcome");
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal()));
        when(publicationService.browseWorkspaceEvents(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());

        var landing = landing();
        landing.beforeEnter(event);

        when(localization.i18n(anyString())).thenReturn("Second welcome");
        landing.localeChange(null);

        assertThat(landing.welcomeText()).isEqualTo("Second welcome");
    }

    @Test
    void eventFeed_rendersTheNestedAudienceLocationName() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticationContext.getAuthenticatedUser(AuthenticatedUserPrincipal.class))
                .thenReturn(Optional.of(principal()));
        var audienceEvent = WorkspaceEventAudienceModel.builder()
                .uniqueId(new UniqueId(2345L)).title("Training")
                .startAt(Instant.parse("2026-09-30T12:00:00Z")).eventType(WorkspaceEventType.PADEL)
                .maxParticipantCount(8).isPublished(true).membershipBound(true)
                .location(LocationAudienceModel.builder().name("Court 1").build()).build();
        when(publicationService.browseWorkspaceEvents(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(audienceEvent)));

        var landing = landing();
        landing.beforeEnter(event);

        assertThat(descendants(landing).filter(Span.class::isInstance).map(Span.class::cast).map(Span::getText))
                .contains("Court 1");
    }

    private LandingView landing() {
        return new LandingView(localization, authenticationContext, publicationService, membershipRequestService);
    }

    private AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal(new UniqueId(1234L), "Test User", Set.of(), true,
                AuthenticationFlow.TELEGRAM);
    }

    private static Stream<Component> descendants(Component component) {
        return component.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
