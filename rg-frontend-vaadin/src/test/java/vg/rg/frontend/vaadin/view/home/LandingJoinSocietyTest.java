package vg.rg.frontend.vaadin.view.home;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.model.security.AuthenticatedUserPrincipal;
import vg.rg.model.security.AuthenticationFlow;
import vg.rg.model.workspace.WorkspaceEventAudienceModel;
import vg.rg.model.workspace.WorkspaceEventType;
import vg.rg.service.resource.ResourcePublicationService;
import vg.rg.service.workspace.WorkspaceMembershipRequestService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandingJoinSocietyTest {

    private static final UniqueId EVENT = new UniqueId(456L);
    @Mock LocalizationService localization;
    @Mock AuthenticationContext authentication;
    @Mock ResourcePublicationService publications;
    @Mock WorkspaceMembershipRequestService membership;
    @Mock VaadinSession session;
    private RecordingUi ui;

    @BeforeEach
    void attachUi() {
        ui = new RecordingUi();
        UI.setCurrent(ui);
        when(localization.i18n(anyString())).thenAnswer(call -> call.getArgument(0));
        when(authentication.getAuthenticatedUser(AuthenticatedUserPrincipal.class)).thenReturn(Optional.of(
                AuthenticatedUserPrincipal.builder().permissions(Set.of())
                        .authenticationFlow(AuthenticationFlow.TELEGRAM).build()));
        when(publications.browseWorkspaceEvents(any())).thenReturn(new PageImpl<>(List.of(
                WorkspaceEventAudienceModel.builder().uniqueId(EVENT).title("Training")
                        .startAt(Instant.parse("2026-09-30T12:00:00Z"))
                        .eventType(WorkspaceEventType.PADEL).isPublished(true).build())));
    }

    @AfterEach
    void detachUi() {
        UI.setCurrent(null);
        ui = null;
    }

    @Test
    void cancellingConsent_doesNotRememberAnIntentOrRequestMembership() {
        try (var intent = mockStatic(JoinSocietyIntent.class)) {
            var dialog = openConsent();

            click(button(dialog, "events.cancel"));

            assertThat(dialog.isOpened()).isFalse();
            intent.verify(() -> JoinSocietyIntent.remember(EVENT), never());
            verifyNoInteractions(membership);
            assertThat(ui.navigatedTo).isNull();
        }
    }

    @Test
    void confirmingConsent_remembersTheSelectedEventAndNavigatesToLogin() {
        try (var intent = mockStatic(JoinSocietyIntent.class)) {
            var dialog = openConsent();
            var continueButton = button(dialog, "home.events.membership.continue");
            assertThat(continueButton.isEnabled()).isFalse();
            var consent = components(dialog.getElement()).filter(Checkbox.class::isInstance)
                    .map(Checkbox.class::cast).findFirst().orElseThrow();

            consent.setValue(true);
            assertThat(continueButton.isEnabled()).isTrue();
            consent.setValue(false);
            assertThat(continueButton.isEnabled()).isFalse();
            consent.setValue(true);
            click(continueButton);

            assertThat(dialog.isOpened()).isFalse();
            intent.verify(() -> JoinSocietyIntent.remember(EVENT));
            assertThat(ui.navigatedTo).isEqualTo("login");
            verifyNoInteractions(membership);
        }
    }

    private Dialog openConsent() {
        var view = new LandingView(localization, authentication, publications, membership);
        ui.add(view);
        view.beforeEnter(mock(BeforeEnterEvent.class));
        click(button(view, "home.events.membership.join"));
        ui.getInternals().setSession(session);
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return ui.getChildren().filter(Dialog.class::isInstance).map(Dialog.class::cast)
                .filter(Dialog::isOpened).findFirst().orElseThrow();
    }

    private static Button button(Component root, String label) {
        var candidates = components(root.getElement());
        if (root instanceof Dialog dialog) {
            candidates = Stream.concat(candidates, components(dialog.getFooter().getElement()));
        }
        return candidates.filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
    }

    private static Stream<Component> components(Element root) {
        return Stream.concat(root.getComponent().stream(), root.getChildren().flatMap(LandingJoinSocietyTest::components));
    }

    private static void click(Button button) {
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
    }

    private static class RecordingUi extends UI {
        private String navigatedTo;

        @Override
        public void navigate(String location) {
            navigatedTo = location;
        }
    }
}
