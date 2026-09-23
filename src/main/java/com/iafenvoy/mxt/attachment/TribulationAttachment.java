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
 * The running tribulation: which definition accepted the attempt, the wind-up left and the beats still to consume.
 * The queue is the cursor - a run copies the definition's timeline, which is also what stops a datapack reload from
 * changing a run already under way - and an empty {@link #state()} is what makes the head beat's start fire once.
 */
public final class TribulationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<TribulationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Tribulation.CODEC.optionalFieldOf("tribulation").forGetter(TribulationAttachment::tribulation),
            TimelineEntry.CODEC.listOf().optionalFieldOf("timeline", List.of()).forGetter(TribulationAttachment::beats),
            DataStorage.CODEC.optionalFieldOf("state").forGetter(TribulationAttachment::state),
            Codec.LONG.optionalFieldOf("windup", 0L).forGetter(TribulationAttachment::windup)
    ).apply(i, TribulationAttachment::new));
    private Optional<Holder<Tribulation>> tribulation;
    private final Deque<TimelineEntry> queue;
    private Optional<DataStorage> state;
    private long windup;

    public TribulationAttachment() {
        this(Optional.empty(), List.of(), Optional.empty(), 0L);
    }

    private TribulationAttachment(Optional<Holder<Tribulation>> tribulation, List<TimelineEntry> timeline,
                                  Optional<DataStorage> state, long windup) {
        this.tribulation = tribulation;
        this.queue = new ArrayDeque<>(timeline);
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

    // Called instead of touching the queue, so nothing is consumed and no beat begins while counting in.
    public void tickWindup() {
        if (this.windup <= 0L) return;
        this.windup--;
        this.markDirty();
    }

    public TimelineEntry peek() {
        return this.queue.peek();
    }

    // Finishing, failing or skipping the head beat all come through here: the next beat brings a state of its own,
    // and an empty state is what makes it begin.
    public void poll() {
        this.queue.poll();
        this.state = Optional.empty();
        this.markDirty();
    }

    public int remaining() {
        return this.queue.size();
    }

    // Derived rather than stored: a reload that changes the definition's length shifts this number, and only this one.
    public int consumed() {
        return this.tribulation.map(holder -> holder.value().timeline().size() - this.queue.size()).orElse(0);
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
        this.queue.clear();
        this.queue.addAll(timeline);
        this.state = Optional.empty();
        this.windup = Math.max(0L, windup);
        this.markDirty();
    }

    public void clear() {
        this.tribulation = Optional.empty();
        this.queue.clear();
        this.state = Optional.empty();
        this.windup = 0L;
        this.markDirty();
    }

    private List<TimelineEntry> beats() {
        return List.copyOf(this.queue);
    }
}
