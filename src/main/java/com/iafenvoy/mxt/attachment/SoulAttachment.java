package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional soul-form state retained after a death transition.
 */
public final class SoulAttachment extends ShouldSyncAttachment {
    public static final MapCodec<SoulAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(SoulAttachment::active),
            Codec.STRING.optionalFieldOf("origin", "").forGetter(SoulAttachment::origin),
            Codec.LONG.optionalFieldOf("created_at", -1L).forGetter(SoulAttachment::createdAt),
            Codec.STRING.optionalFieldOf("source", "").forGetter(SoulAttachment::source),
            Codec.STRING.optionalFieldOf("manifestation", "").forGetter(SoulAttachment::manifestationValue)
    ).apply(i, SoulAttachment::new));
    private boolean active;
    private String origin;
    private long createdAt;
    private String source, manifestation;

    public SoulAttachment() {
        this(false, "", -1L, "", "");
    }

    private SoulAttachment(boolean active, String origin, long createdAt, String source, String manifestation) {
        this.active = active;
        this.origin = origin;
        this.createdAt = createdAt;
        this.source = source;
        this.manifestation = manifestation;
    }

    public boolean active() {
        return this.active;
    }

    public String origin() {
        return this.origin;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public String source() {
        return this.source;
    }

    public Optional<UUID> manifestation() {
        try {
            return this.manifestation.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(this.manifestation));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String manifestationValue() {
        return this.manifestation;
    }

    public void activate(UUID origin, long gameTime, String source, UUID manifestation) {
        this.active = true;
        this.origin = origin.toString();
        this.createdAt = gameTime;
        this.source = source == null ? "" : source;
        this.manifestation = manifestation == null ? "" : manifestation.toString();
        this.markDirty();
    }

    public void clear() {
        this.active = false;
        this.origin = "";
        this.createdAt = -1L;
        this.source = "";
        this.manifestation = "";
        this.markDirty();
    }
}
