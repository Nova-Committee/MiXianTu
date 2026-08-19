package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Tribulation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * Active tribulation cursor. A missing definition disables progression rather than discarding the cursor.
 */
public final class TribulationData {
    public static final MapCodec<TribulationData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Tribulation.CODEC.optionalFieldOf("tribulation").forGetter(TribulationData::tribulation),
            Codec.INT.optionalFieldOf("phase", 0).forGetter(TribulationData::phase),
            Codec.LONG.optionalFieldOf("phase_ends_at", -1L).forGetter(TribulationData::phaseEndsAt),
            Codec.BOOL.optionalFieldOf("paused", false).forGetter(TribulationData::paused)
    ).apply(i, TribulationData::new));
    private Optional<Holder<Tribulation>> tribulation;
    private int phase;
    private long phaseEndsAt;
    private boolean paused;

    public TribulationData() {
        this(Optional.empty(), 0, -1L, false);
    }

    private TribulationData(Optional<Holder<Tribulation>> tribulation, int phase, long phaseEndsAt, boolean paused) {
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
    }

    public void clear() {
        this.tribulation = Optional.empty();
        this.phase = 0;
        this.phaseEndsAt = -1L;
        this.paused = false;
    }

    public void setPaused(boolean value) {
        this.paused = value;
    }
}
