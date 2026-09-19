package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The running tribulation: which definition accepted the attempt, the wind-up it still has to wait out, the beats
 * that are still to be consumed, and the state of the beat at the head of that queue.
 *
 * <p>The queue <em>is</em> the cursor. Starting a run copies the definition's timeline into it, the head is the
 * beat being consumed, and finishing a beat pops it, so nothing has to be kept in step with a list index. The
 * copy is also what makes a datapack reload unable to change a run that is already under way: the beats left are
 * the copies, and the definition is read only for its difficulty scale and its two endings. An empty queue is a
 * run with nothing left in it, which the consumer either completes or drops.</p>
 *
 * <p>Only one beat runs at a time, so a run keeps a single state value rather than a store: {@link #state()} is
 * that value, written by the beat at the head and by nothing else. An empty state means that beat has not begun,
 * which is what makes its start fire once and only once. The wind-up lives beside it rather than in it, because
 * it belongs to the run and not to a beat: while {@link #windup()} is positive no beat has begun and the queue
 * has not been touched, so the countdown survives a restart exactly like the queue does.</p>
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

    /**
     * How many ticks of the run's wind-up are left, zero once the timeline is the thing running. The countdown is
     * resolved when the run starts and then only ever counts down, so it is what the run will really wait out.
     */
    public long windup() {
        return this.windup;
    }

    /**
     * Spends one tick of the wind-up. The consumer calls this instead of touching the queue, so nothing is
     * consumed and no beat begins while the run is still counting itself in.
     */
    public void tickWindup() {
        if (this.windup <= 0L) return;
        this.windup--;
        this.markDirty();
    }

    /**
     * The beat being consumed: the head of the queue, or null once the run has nothing left.
     */
    public TimelineEntry peek() {
        return this.queue.peek();
    }

    /**
     * Drops the beat at the head together with its state, which is what finishing it, failing it or skipping it
     * does. The next beat brings a state of its own, and an empty state is what makes it begin.
     */
    public void poll() {
        this.queue.poll();
        this.state = Optional.empty();
        this.markDirty();
    }

    /**
     * How many beats are left, counting the one at the head.
     */
    public int remaining() {
        return this.queue.size();
    }

    /**
     * How many beats have been consumed so far, which is the ordinal an entry event reports. It is derived
     * rather than stored: the definition still knows how long the run was, so nothing has to be kept in step
     * with the queue. A reload that changes the definition's length shifts this number, and only this number.
     */
    public int consumed() {
        return this.tribulation.map(holder -> holder.value().timeline().size() - this.queue.size()).orElse(0);
    }

    /**
     * The state of the beat at the head, or empty while that beat has not begun. The consumer works on a draft of
     * this and commits it back once the beat has answered its tick.
     */
    public Optional<DataStorage> state() {
        return this.state;
    }

    public void setState(Optional<DataStorage> state) {
        if (Objects.equals(this.state, state)) return;
        this.state = state;
        this.markDirty();
    }

    /**
     * Installs a run: the definition's timeline is copied into the queue and the wind-up it has to wait out is
     * stored with it. The countdown runs down first, and the first beat begins on the tick after it reaches zero.
     */
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

    /**
     * What is left of the run, in the form the codec writes.
     */
    private List<TimelineEntry> beats() {
        return List.copyOf(this.queue);
    }
}
