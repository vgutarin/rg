package vg.rg.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.security.AuthorityChecker;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtectedActionServiceImplTest {

    @Mock AuthorityChecker authorityChecker;

    @Test
    void values_serviceContract_returnsOnlyTerminalStates() {
        assertThat(ProtectedActionService.State.values()).containsExactly(
                ProtectedActionService.State.COMPLETED,
                ProtectedActionService.State.DENIED);
    }

    @Test
    void submit_repeatedIdempotencyKey_returnsPriorResultWithoutDuplicateEffect() {
        when(authorityChecker.currentSubject()).thenReturn(Optional.of("subject-1234"));
        var effects = new AtomicInteger();
        var service = new ProtectedActionServiceImpl(
                authorityChecker, (operationId, subject) -> effects.incrementAndGet());
        var key = UUID.randomUUID();

        var first = service.submit(key);
        var repeated = service.submit(key);

        assertThat(first.state()).isEqualTo(ProtectedActionService.State.COMPLETED);
        assertThat(repeated).isEqualTo(first);
        assertThat(effects).hasValue(1);
    }

    @Test
    void submit_sameIdempotencyKeyFromDifferentSubject_returnsDenied() {
        when(authorityChecker.currentSubject())
                .thenReturn(Optional.of("subject-1234"))
                .thenReturn(Optional.of("subject-5678"));
        var service = new ProtectedActionServiceImpl(authorityChecker, (operationId, subject) -> { });
        var key = UUID.randomUUID();
        assertThat(service.submit(key).state()).isEqualTo(ProtectedActionService.State.COMPLETED);
        assertThat(service.submit(key).state()).isEqualTo(ProtectedActionService.State.DENIED);
    }

    @Test
    void submit_missingSubject_returnsStableDenialWithoutCreatingOwnershipOrEffect() {
        when(authorityChecker.currentSubject())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("subject-1234"));
        var effects = new AtomicInteger();
        var service = new ProtectedActionServiceImpl(
                authorityChecker, (operationId, subject) -> effects.incrementAndGet());
        var key = UUID.randomUUID();

        var first = service.submit(key);
        var repeated = service.submit(key);
        var established = service.submit(key);

        assertThat(first.state()).isEqualTo(ProtectedActionService.State.DENIED);
        assertThat(repeated).isEqualTo(first);
        assertThat(established.state()).isEqualTo(ProtectedActionService.State.COMPLETED);
        assertThat(effects).hasValue(1);
    }
}
