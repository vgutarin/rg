package vg.rg.model.workspace;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The contact data a workspace owner records about a participant, and the value that is persisted —
 * serialized to JSON and encrypted, as one opaque column.
 *
 * <p><strong>What may go in here: contact attributes of the registered person, and nothing else.</strong>
 * The constitution's allowance for this data (Principle I) is exhaustive, so an extra field is a
 * governance change and not merely a schema one. Without that rule stated, an untyped-looking blob
 * becomes a dumping ground.
 *
 * <p>One record rather than a column per field, because encryption already destroys the only advantage
 * separate columns would have: neither form can be queried, sorted, or constrained by the database.
 * Splitting would buy nothing and cost one envelope per field, one wiring site per field, and a
 * migration per field added. It is an untyped bag only at the storage layer — here it is an explicit
 * type, and JSON is merely its wire form.
 *
 * <p>{@code @JsonProperty} on every component pins the wire names, so renaming a component in Java can
 * never orphan already-stored rows. (Records would deserialize without it, because component names live
 * in the class file rather than depending on {@code -parameters}; the point is the pinning, not the
 * mechanism.)
 *
 * @param schemaVersion the shape this payload is in. The stored envelope carries no version byte — see
 *                      {@code specs/current/encryption.md} — so the discriminator lives in the payload
 *                      instead. Lenient parsing already absorbs an <em>added</em> field; this earns its
 *                      keep when an existing field has to be reinterpreted, which would otherwise mean
 *                      rewriting every stored row.
 * @param label         the display name the registering owner chose. Never derived from Telegram data
 *                      and never an identity, authorization, ownership, or audit input.
 * @param phone         a single contact number, normalized by {@code ParticipantPhone}. Nullable: a
 *                      participant may be recorded with a label alone. Null is omitted from the JSON
 *                      rather than written, so an absent phone costs no envelope space.
 */
public record ParticipantDescriptor(
        @JsonProperty("v") int schemaVersion,
        @JsonProperty("l") String label,
        @JsonProperty("p") String phone) {

    /** The shape {@link #of} produces. Bump only when an existing field changes meaning. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Builds a descriptor in the current shape. Callers never choose the version themselves. */
    public static ParticipantDescriptor of(String label, String phone) {
        return new ParticipantDescriptor(CURRENT_SCHEMA_VERSION, label, phone);
    }

    /**
     * Redacted deliberately. A record's generated {@code toString()} prints every component, so the
     * default would put a label and a phone number into any log line, exception message, or debugger
     * expression that touched one — which is exactly the exposure this type exists to prevent.
     */
    @Override
    public String toString() {
        return "ParticipantDescriptor[schemaVersion=" + schemaVersion + ", label=***, phone=***]";
    }
}
