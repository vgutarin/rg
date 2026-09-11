package vg.rg.model.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceEventTypeTest {

    @Test
    void persistedOrdinals_areStable() {
        assertThat(WorkspaceEventType.PADEL.ordinal()).isZero();
        assertThat(WorkspaceEventType.TENNIS.ordinal()).isEqualTo(1);
    }
}
