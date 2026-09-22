package vg.rg.model.resource;

import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Publication state and policy for one resource. */
public record ResourcePublication(
        UniqueId resourceUniqueId,
        int version,
        Instant visibleSince,
        ResourceVisibilityMode visibilityMode,
        List<ResourceVisibilityRule> rules) {

    public ResourcePublication {
        Objects.requireNonNull(resourceUniqueId, "resourceUniqueId");
        Objects.requireNonNull(visibilityMode, "visibilityMode");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public boolean isPublished() {
        return visibleSince != null;
    }
}
