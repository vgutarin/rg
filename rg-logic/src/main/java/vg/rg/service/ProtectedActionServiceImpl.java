package vg.rg.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vg.rg.security.AuthorityChecker;
import vg.rg.security.model.Permissions;
import vg.unique.id.model.UniqueId;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProtectedActionServiceImpl implements ProtectedActionService {

    @FunctionalInterface
    public interface ProtectedEffect {
        void execute(UUID operationId, UniqueId userUniqueId);
    }

    private static final class Entry {
        private final UniqueId userUniqueId;
        private final UUID operationId = UUID.randomUUID();
        private Result result;

        private Entry(UniqueId userUniqueId) {
            this.userUniqueId = userUniqueId;
        }
    }

    private final AuthorityChecker authorityChecker;
    private final ProtectedEffect effect;
    private final ConcurrentHashMap<UUID, Entry> operations = new ConcurrentHashMap<>();

    @Autowired
    public ProtectedActionServiceImpl(AuthorityChecker authorityChecker) {
        this(authorityChecker, (operationId, uniqueId) -> {
            // Representative development effect. Production extraction replaces this boundary.
        });
    }

    public ProtectedActionServiceImpl(AuthorityChecker authorityChecker, ProtectedEffect effect) {
        this.authorityChecker = java.util.Objects.requireNonNull(authorityChecker);
        this.effect = java.util.Objects.requireNonNull(effect);
    }

    @Override
    @PreAuthorize("@authorityChecker.hasAuthority('" + Permissions.Request.SUBMIT + "')")
    public Result submit(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        var userUniqueId = authorityChecker.currentUserUniqueId();
        if (userUniqueId.isEmpty()) {
            return deniedWithoutState(idempotencyKey);
        }
        var entry = operations.computeIfAbsent(
                idempotencyKey, ignored -> new Entry(userUniqueId.orElseThrow()));
        synchronized (entry) {
            if (!entry.userUniqueId.equals(userUniqueId.orElseThrow())) {
                return result(entry, idempotencyKey, State.DENIED, "request.denied");
            }
            if (entry.result != null) {
                return entry.result;
            }
            effect.execute(entry.operationId, entry.userUniqueId);
            entry.result = result(entry, idempotencyKey, State.COMPLETED, "request.submitted");
            return entry.result;
        }
    }

    private Result deniedWithoutState(UUID idempotencyKey) {
        var operationId = UUID.nameUUIDFromBytes(
                ("missing-subject:" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return new Result(operationId, idempotencyKey, State.DENIED, "request.denied");
    }

    private Result result(Entry entry, UUID key, State state, String messageKey) {
        return new Result(entry.operationId, key, state, messageKey);
    }
}
