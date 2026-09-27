package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Where one creature sits on the creature it rides. The offset is already resolved - naming a side belongs to
 * whoever writes the record - and y counts down from the vehicle's current top, so a vehicle that changes height
 * with its pose takes the perched creature along.
 */
public final class PerchAttachment extends ShouldSyncAttachment {
    public static final MapCodec<PerchAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.lenientOptionalFieldOf("offset").forGetter(PerchAttachment::offset)
    ).apply(i, PerchAttachment::new));
    private Vec3 offset;

    public PerchAttachment() {
    }

    private PerchAttachment(Optional<Vec3> offset) {
        this.offset = offset.orElse(null);
    }

    public Optional<Vec3> offset() {
        return Optional.ofNullable(this.offset);
    }

    public boolean perched() {
        return this.offset != null;
    }

    public void set(Vec3 offset) {
        this.offset = offset;
        this.markDirty();
    }

    public void clear() {
        this.offset = null;
        this.markDirty();
    }
}
