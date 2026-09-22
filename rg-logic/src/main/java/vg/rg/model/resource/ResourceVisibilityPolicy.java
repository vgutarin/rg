package vg.rg.model.resource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable visibility policy for a published resource. */
public record ResourceVisibilityPolicy(ResourceVisibilityMode mode, List<ResourceVisibilityRule> rules) {

    public ResourceVisibilityPolicy {
        Objects.requireNonNull(mode, "mode");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        var targets = new LinkedHashSet<PolicyTarget>();
        for (var rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (!targets.add(new PolicyTarget(rule.subjectKind(), rule.subjectUniqueId()))) {
                throw new IllegalArgumentException("resource.visibility.error.duplicate-rule-target");
            }
        }
        if (mode == ResourceVisibilityMode.PUBLIC && !rules.isEmpty()) {
            throw new IllegalArgumentException("resource.visibility.error.public-rules");
        }
    }

    public static ResourceVisibilityPolicy publicVisibility() {
        return new ResourceVisibilityPolicy(ResourceVisibilityMode.PUBLIC, List.of());
    }

    public static ResourceVisibilityPolicy restricted(List<ResourceVisibilityRule> rules) {
        return new ResourceVisibilityPolicy(ResourceVisibilityMode.RESTRICTED, rules);
    }

    private record PolicyTarget(ResourceVisibilitySubjectKind kind, Object id) { }
}
