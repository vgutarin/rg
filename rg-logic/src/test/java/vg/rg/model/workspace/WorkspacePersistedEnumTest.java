package vg.rg.model.workspace;

import org.junit.jupiter.api.Test;
import vg.rg.model.resource.ResourceVisibilityEffect;
import vg.rg.model.resource.ResourceVisibilityMode;
import vg.rg.model.resource.ResourceVisibilitySubjectKind;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks each JPA-persisted enum's symbolic value to its stored ordinal. */
class WorkspacePersistedEnumTest {

    @Test
    void eventType_ordinalsAreStable() {
        assertNameToOrdinal(WorkspaceEventType.class, "PADEL=0", "TENNIS=1");
    }

    @Test
    void visibilityMode_ordinalsAreStable() {
        assertNameToOrdinal(ResourceVisibilityMode.class, "PUBLIC=0", "RESTRICTED=1");
    }

    @Test
    void visibilityEffect_ordinalsAreStable() {
        assertNameToOrdinal(ResourceVisibilityEffect.class, "ALLOW=0", "DENY=1");
    }

    @Test
    void visibilitySubjectKind_ordinalsAreStable() {
        assertNameToOrdinal(ResourceVisibilitySubjectKind.class,
                "USER=0", "WORKSPACE=1", "WORKSPACE_GROUP=2");
    }

    private static <T extends Enum<T>> void assertNameToOrdinal(Class<T> enumType, String... expected) {
        assertThat(Arrays.stream(enumType.getEnumConstants())
                .map(value -> value.name() + "=" + value.ordinal()))
                .containsExactly(expected);
    }
}
