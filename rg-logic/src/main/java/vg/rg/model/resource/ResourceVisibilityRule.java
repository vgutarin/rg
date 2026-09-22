package vg.rg.model.resource;

import vg.unique.id.model.UniqueId;

import java.util.Objects;

/** One typed audience rule for a published resource. */
public record ResourceVisibilityRule(
        ResourceVisibilityEffect effect,
        ResourceVisibilitySubjectKind subjectKind,
        UniqueId subjectUniqueId) {

    public ResourceVisibilityRule {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(subjectKind, "subjectKind");
        Objects.requireNonNull(subjectUniqueId, "subjectUniqueId");
    }
}
