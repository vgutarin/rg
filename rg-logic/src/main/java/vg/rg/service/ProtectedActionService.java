package vg.rg.service;

import java.util.UUID;

public interface ProtectedActionService {

    enum State { COMPLETED, DENIED }

    record Result(UUID operationId, UUID idempotencyKey, State state, String messageKey) {
        public Result {
            if (operationId == null || idempotencyKey == null || state == null
                    || messageKey == null || messageKey.isBlank()) {
                throw new IllegalArgumentException("Invalid protected action result");
            }
        }
    }

    Result submit(UUID idempotencyKey);
}
