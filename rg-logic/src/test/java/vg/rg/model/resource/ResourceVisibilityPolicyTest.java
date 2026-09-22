package vg.rg.model.resource;

import org.junit.jupiter.api.Test;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceVisibilityPolicyTest {

    @Test
    void publicPolicy_rejectsRules() {
        assertThatThrownBy(() -> new ResourceVisibilityPolicy(ResourceVisibilityMode.PUBLIC, List.of(
                rule(ResourceVisibilityEffect.ALLOW, ResourceVisibilitySubjectKind.USER, 1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resource.visibility.error.public-rules");
    }

    @Test
    void restrictedPolicy_rejectsDuplicateTargets() {
        assertThatThrownBy(() -> ResourceVisibilityPolicy.restricted(List.of(
                rule(ResourceVisibilityEffect.ALLOW, ResourceVisibilitySubjectKind.USER, 1L),
                rule(ResourceVisibilityEffect.DENY, ResourceVisibilitySubjectKind.USER, 1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resource.visibility.error.duplicate-rule-target");
    }

    private static ResourceVisibilityRule rule(ResourceVisibilityEffect effect, ResourceVisibilitySubjectKind kind,
                                               long subjectId) {
        return new ResourceVisibilityRule(effect, kind, new UniqueId(subjectId));
    }
}
