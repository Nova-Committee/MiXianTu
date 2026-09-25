package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.*;

/**
 * The running tribulation: which definition accepted the attempt, the wind-up left, the beats of the run and the
 * cursor standing on the one being consumed. The cursor is stored rather than derived, because a branch beat can
 * send the run backwards; an empty {@link #state()} is what makes the head beat's start fire once.
 */
public final class TribulationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<TribulationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Tribulation.CODEC.lenientOptionalFieldOf("tribulation").forGetter(TribulationAttachment::tribulation),
            TimelineEntry.CODEC.listOf().lenientOptionalFieldOf("timeline", List.of()).forGetter(TribulationAttachment::beats),
            Codec.INT.lenientOptionalFieldOf("cursor", 0).forGetter(TribulationAttachment::cursor),
            DataStorage.CODEC.lenientOptionalFieldOf("state").forGetter(TribulationAttachment::state),
            Codec.LONG.lenientOptionalFieldOf("windup", 0L).forGetter(TribulationAttachment::windup)
    ).apply(i, TribulationAttachment::new));
    private Optional<Holder<Tribulation>> tribulation;
    private final List<TimelineEntry> timeline;
    private int cursor;
    private Optional<DataStorage> state;
    private long windup;

    public TribulationAttachment() {
        this(Optional.empty(), List.of(), 0, Optional.empty(), 0L);
    }

    private TribulationAttachment(Optional<Holder<Tribulation>> tribulation, List<TimelineEntry> timeline, int cursor,
                                  Optional<DataStorage> state, long windup) {
        this.tribulation = tribulation;
        this.timeline = new ArrayList<>(timeline);
        this.cursor = cursor;
        this.state = state;
        this.windup = windup;
    }

    public Optional<Holder<Tribulation>> tribulation() {
        return this.tribulation;
    }

    // Resolved when the run starts and only ever counting down, so it is what the run will really wait out.
    public long windup() {
        return this.windup;
    }

    // Called instead of touching the cursor, so nothing is consumed and no beat begins while counting in.
    public void tickWindup() {
        if (this.windup <= 0L) return;
        this.windup--;
        this.markDirty();
    }

    public TimelineEntry peek() {
        return this.cursor < this.timeline.size() ? this.timeline.get(this.cursor) : null;
    }

    // The beat being consumed; also how many beats of the run have already been consumed.
    public int cursor() {
        return this.cursor;
    }

    public int length() {
        return this.timeline.size();
    }

    // Finishing, failing, skipping and jumping all come through here: the next beat brings a state of its own, and
    // an empty state is what makes it begin. A target of {@link TimelineJump#NEXT} moves on by one beat.
    public void advance(int target) {
        this.cursor = target < 0 ? this.cursor + 1 : Math.min(target, this.timeline.size());
        this.state = Optional.empty();
        this.markDirty();
    }

    public int remaining() {
        return Math.max(0, this.timeline.size() - this.cursor);
    }

    // Derived rather than stored: a reload that changes the definition's length shifts this number, and only this.
    public int consumed() {
        return this.cursor;
    }

    // The consumer works on a draft of this and commits it back once the beat has answered its tick.
    public Optional<DataStorage> state() {
        return this.state;
    }

    public void setState(Optional<DataStorage> state) {
        if (Objects.equals(this.state, state)) return;
        this.state = state;
        this.markDirty();
    }

    // The definition's timeline is copied in, and the first beat begins the tick after the wind-up reaches zero.
    public void start(Holder<Tribulation> tribulation, List<TimelineEntry> timeline, long windup) {
        this.tribulation = Optional.of(tribulation);
        this.timeline.clear();
        this.timeline.addAll(timeline);
        this.cursor = 0;
        this.state = Optional.empty();
        this.windup = Math.max(0L, windup);
        this.markDirty();
    }

    public void clear() {
        this.tribulation = Optional.empty();
        this.timeline.clear();
        this.cursor = 0;
        this.state = Optional.empty();
        this.windup = 0L;
        this.markDirty();
    }

    private List<TimelineEntry> beats() {
        return List.copyOf(this.timeline);
    }
}
