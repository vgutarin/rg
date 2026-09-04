package vg.rg.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import static vg.rg.config.ConfigurationBounds.positiveBytesOrDefault;
import static vg.rg.config.ConfigurationBounds.positiveIntOrDefault;

/**
 * Application-owned workspace configuration. Values come from runtime configuration; sensible defaults
 * keep the layer functional out of the box.
 *
 * <p>There is deliberately no containment-depth bound: an authority check resolves a resource to its
 * workspace with a single permission-dispatched query and performs no runtime traversal, so there is no
 * loop to bound.
 *
 * <p>Bound through {@code @ConfigurationProperties}, with the constructor taking {@code String}s so that
 * {@link ConfigurationBounds} — not Spring's converter — reports a rejected value, and therefore never
 * echoes it. See {@link GeoProperties} and {@link ConfigurationBounds}.
 *
 * <p>Registered explicitly in {@code RgLogicConfig}: {@code @ConfigurationProperties} types are not
 * component-scanned.
 */
@ConfigurationProperties(WorkspaceProperties.PREFIX)
public final class WorkspaceProperties {

    static final String PREFIX = "rg.workspace";

    private static final int DEFAULT_MAX_PER_USER = 20;
    private static final int DEFAULT_NAME_MAX_LENGTH = 128;
    private static final int DEFAULT_DESCRIPTION_MAX_LENGTH = 1024;
    private static final int DEFAULT_PARTICIPANTS_MAX_PER_WORKSPACE = 1024;
    private static final DataSize DEFAULT_PARTICIPANT_DESCRIPTOR_MAX_SIZE = DataSize.ofBytes(2048);
    private static final int DEFAULT_PARTICIPANT_LABEL_MAX_LENGTH = 128;

    /** How many workspaces one user may own. Must be positive; defaults to 20. */
    private final int maxPerUser;

    /**
     * Bound on a user-authored workspace name. A system-created workspace stores no name at all, so this
     * constrains user input only. Must be positive; defaults to 128.
     */
    private final int nameMaxLength;

    /** Bound on a user-authored workspace description. Must be positive; defaults to 1024. */
    private final int descriptionMaxLength;

    /**
     * How many participants one workspace may hold. Must be positive; defaults to 1024.
     *
     * <p>This is the documented resource-control strategy for the roster, and it is what makes the
     * index-free duplicate check defensible: a participant's contact data is encrypted, so the column
     * can be neither searched nor sorted in SQL, and registration therefore opens every existing
     * envelope in the workspace to compare phone numbers. Bounding the count bounds that work.
     */
    private final int participantsMaxPerWorkspace;

    /**
     * Bound on one participant's sealed descriptor, in bytes. Must be positive; defaults to 2048.
     *
     * <p>Measured on <strong>the envelope, which is what the column receives</strong>, because that is
     * the DB-facing number. The JSON budget follows from the stored format: envelope = JSON bytes + 13
     * header + 16 GCM tag, so the default allows roughly 2019 bytes of JSON.
     */
    private final long participantDescriptorMaxBytes;

    /**
     * Bound on a participant's display label. Must be positive; defaults to 128.
     *
     * <p>Separate from {@link #participantDescriptorMaxBytes} so an over-long label is reported as a
     * field error the user can act on, rather than as a size failure over an opaque blob.
     */
    private final int participantLabelMaxLength;

    /**
     * {@code @Builder} sits on the constructor rather than the class deliberately: the class has an
     * explicit constructor, so Lombok would not synthesise an all-args one, and a class-level builder
     * would be generated from the {@code int}/{@code long} fields instead of the {@code String}
     * parameters this holder must take. It also keeps six same-typed arguments from being passed
     * positionally, where adding a bound silently reorders every existing call.
     *
     * <p>Spring's constructor binding is unaffected — this adds a static factory, not a constructor.
     */
    @Builder
    public WorkspaceProperties(
            String maxPerUser,
            String nameMaxLength,
            String descriptionMaxLength,
            String participantsMaxPerWorkspace,
            String participantDescriptorMaxBytes,
            String participantLabelMaxLength) {
        this.maxPerUser = positiveIntOrDefault(
                maxPerUser, DEFAULT_MAX_PER_USER, PREFIX + ".max-per-user");
        this.nameMaxLength = positiveIntOrDefault(
                nameMaxLength, DEFAULT_NAME_MAX_LENGTH, PREFIX + ".name-max-length");
        this.descriptionMaxLength = positiveIntOrDefault(
                descriptionMaxLength, DEFAULT_DESCRIPTION_MAX_LENGTH, PREFIX + ".description-max-length");
        this.participantsMaxPerWorkspace = positiveIntOrDefault(
                participantsMaxPerWorkspace,
                DEFAULT_PARTICIPANTS_MAX_PER_WORKSPACE,
                PREFIX + ".participants-max-per-workspace");
        this.participantDescriptorMaxBytes = positiveBytesOrDefault(
                participantDescriptorMaxBytes,
                DEFAULT_PARTICIPANT_DESCRIPTOR_MAX_SIZE,
                PREFIX + ".participant-descriptor-max-bytes");
        this.participantLabelMaxLength = positiveIntOrDefault(
                participantLabelMaxLength,
                DEFAULT_PARTICIPANT_LABEL_MAX_LENGTH,
                PREFIX + ".participant-label-max-length");
    }

    public int maxPerUser() {
        return maxPerUser;
    }

    public int nameMaxLength() {
        return nameMaxLength;
    }

    public int descriptionMaxLength() {
        return descriptionMaxLength;
    }

    public int participantsMaxPerWorkspace() {
        return participantsMaxPerWorkspace;
    }

    public long participantDescriptorMaxBytes() {
        return participantDescriptorMaxBytes;
    }

    public int participantLabelMaxLength() {
        return participantLabelMaxLength;
    }
}
