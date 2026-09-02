package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * Active tribulation cursor. A missing definition disables progression rather than discarding the cursor.
 */
public final class TribulationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<TribulationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Tribulation.CODEC.optionalFieldOf("tribulation").forGetter(TribulationAttachment::tribulation),
            Codec.INT.optionalFieldOf("phase", 0).forGetter(TribulationAttachment::phase),
            Codec.LONG.optionalFieldOf("phase_ends_at", -1L).forGetter(TribulationAttachment::phaseEndsAt),
            Codec.BOOL.optionalFieldOf("paused", false).forGetter(TribulationAttachment::paused)
    ).apply(i, TribulationAttachment::new));
    private Optional<Holder<Tribulation>> tribulation;
    private int phase;
    private long phaseEndsAt;
    private boolean paused;

    public TribulationAttachment() {
        this(Optional.empty(), 0, -1L, false);
    }

    private TribulationAttachment(Optional<Holder<Tribulation>> tribulation, int phase, long phaseEndsAt, boolean paused) {
        this.tribulation = tribulation;
        this.phase = phase;
        this.phaseEndsAt = phaseEndsAt;
        this.paused = paused;
    }

    public Optional<Holder<Tribulation>> tribulation() {
        return this.tribulation;
    }

    public int phase() {
        return this.phase;
    }

    public long phaseEndsAt() {
        return this.phaseEndsAt;
    }

    public boolean paused() {
        return this.paused;
    }

    public void start(Holder<Tribulation> tribulation, int phase, long endsAt) {
        this.tribulation = Optional.of(tribulation);
        this.phase = phase;
        this.phaseEndsAt = endsAt;
        this.paused = false;
        this.markDirty();
    }

    public void clear() {
        this.tribulation = Optional.empty();
        this.phase = 0;
        this.phaseEndsAt = -1L;
        this.paused = false;
        this.markDirty();
    }

    public void setPaused(boolean value) {
        this.paused = value;
        this.markDirty();
    }
}
