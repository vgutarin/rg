package vg.rg.frontend.vaadin.view.examples.datetime;

import com.vaadin.flow.router.BeforeEnterEvent;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.frontend.vaadin.service.LocalizationService;
import vg.rg.frontend.vaadin.view.auth.NoAccessView;
import vg.rg.model.security.Permissions;
import vg.rg.service.security.AuthorityChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DateTimeExamplesViewTest {

    @Mock LocalizationService localization;
    @Mock AuthorityChecker authorityChecker;
    @Mock BeforeEnterEvent event;

    @Test
    void routeKeepsVaadinAuthenticationButUsesTheExperimentParticipantGuard() {
        assertThat(DateTimeExamplesView.class.isAnnotationPresent(PermitAll.class)).isTrue();
    }

    @Test
    void beforeEnter_experimentParticipantIsAllowed() {
        when(authorityChecker.hasAuthority(Permissions.Experiment.PARTICIPANT)).thenReturn(true);

        view().beforeEnter(event);

        verify(event, never()).rerouteTo(NoAccessView.class);
    }

    @Test
    void beforeEnter_withoutExperimentParticipantReroutesToNoAccess() {
        when(authorityChecker.hasAuthority(Permissions.Experiment.PARTICIPANT)).thenReturn(false);

        view().beforeEnter(event);

        verify(event).rerouteTo(NoAccessView.class);
    }

    private DateTimeExamplesView view() {
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getCurrentLocale()).thenReturn(LocalizationService.DEFAULT_LOCALE);
        return new DateTimeExamplesView(localization, authorityChecker);
    }
}
