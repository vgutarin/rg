package vg.rg.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.security.AuthorityChecker;
import vg.unique.id.model.UniqueId;

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
        when(authorityChecker.currentUserUniqueId()).thenReturn(Optional.of(new UniqueId(1234L)));
        var effects = new AtomicInteger();
        var service = new ProtectedActionServiceImpl(
                authorityChecker, (operationId, userUniqueId) -> effects.incrementAndGet());
        var key = UUID.randomUUID();

        var first = service.submit(key);
        var repeated = service.submit(key);

        assertThat(first.state()).isEqualTo(ProtectedActionService.State.COMPLETED);
        assertThat(repeated).isEqualTo(first);
        assertThat(effects).hasValue(1);
    }

    @Test
    void submit_sameIdempotencyKeyFromDifferentSubject_returnsDenied() {
        when(authorityChecker.currentUserUniqueId())
                .thenReturn(Optional.of(new UniqueId(1234L)))
                .thenReturn(Optional.of(new UniqueId(5678L)));
        var service = new ProtectedActionServiceImpl(authorityChecker, (operationId, userUniqueId) -> { });
        var key = UUID.randomUUID();
        assertThat(service.submit(key).state()).isEqualTo(ProtectedActionService.State.COMPLETED);
        assertThat(service.submit(key).state()).isEqualTo(ProtectedActionService.State.DENIED);
    }

    @Test
    void submit_missingSubject_returnsStableDenialWithoutCreatingOwnershipOrEffect() {
        when(authorityChecker.currentUserUniqueId())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UniqueId(1234L)));
        var effects = new AtomicInteger();
        var service = new ProtectedActionServiceImpl(
                authorityChecker, (operationId, userUniqueId) -> effects.incrementAndGet());
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
