package vg.rg.frontend.vaadin.service;

import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ApplicationSessionAttributesTest {

    @Test
    void typedAttribute_canBeStoredReadAndConsumedOnce() {
        var values = new HashMap<String, Object>();
        var session = mock(VaadinSession.class);
        when(session.getAttribute(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        doAnswer(call -> {
            var name = call.<String>getArgument(0);
            var value = call.getArgument(1);
            if (value == null) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
            return null;
        }).when(session).setAttribute(anyString(), nullable(Object.class));

        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(session);

            ApplicationSessionAttributes.put(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID, 17L);

            assertThat(ApplicationSessionAttributes.get(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID))
                    .contains(17L);
            assertThat(ApplicationSessionAttributes.isPresent(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID))
                    .isTrue();
            assertThat(ApplicationSessionAttributes.consume(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID))
                    .contains(17L);
            assertThat(ApplicationSessionAttributes.consume(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID))
                    .isEmpty();
        }
    }

    @Test
    void readsWithoutCurrentSession_areEmpty() {
        try (var currentSession = mockStatic(VaadinSession.class)) {
            currentSession.when(VaadinSession::getCurrent).thenReturn(null);

            assertThat(ApplicationSessionAttributes.get(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID)).isEmpty();
            assertThat(ApplicationSessionAttributes.consume(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID)).isEmpty();
            assertThat(ApplicationSessionAttributes.isPresent(ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID)).isFalse();
            assertThatThrownBy(() -> ApplicationSessionAttributes.put(
                    ApplicationSessionAttributes.JOIN_SOCIETY_EVENT_ID, 17L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No current Vaadin session");
        }
    }
}
