package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One beat of a timeline, selected by a datapack {@code type}. A consumer walks a timeline one entry at a time
 * and calls {@link #consume(TimelineContext)} every tick while that entry is the current one, so the entry itself
 * decides whether it is instantaneous or a wait. Adding a kind of beat costs one record and one registry line.
 *
 * <p>An entry keeps nothing across ticks: a timeline is copied out of a definition and shared by every run of it.
 * Numbers that have to survive a tick go into {@link TimelineContext#state()}, the slot holding the state of the
 * one entry that is currently running.</p>
 */
public interface TimelineEntry {
    /// A codec rather than a map codec, because a timeline is a <em>list</em> of these and only a codec can
    /// be listed. The dispatch itself is unchanged: one {@code type} field picks the record.
    Codec<TimelineEntry> CODEC = MxtRegistries.TIMELINE_ENTRY_TYPE.byNameCodec()
            .dispatch("type", TimelineEntry::codec, Function.identity());

    MapCodec<? extends TimelineEntry> codec();

    /**
     * Called once, on the tick this entry becomes the current one: the moment to write temporary numbers into
     * {@link TimelineContext#state()}, which is empty here. The consumer has already recorded that the entry
     * began, so an entry that keeps nothing simply writes nothing. A length resolved here is settled for the rest
     * of the beat: a random provider is drawn once, and a later aura change cannot move a wait already running.
     */
    default void begin(TimelineContext context) {
    }

    /**
     * Consumes one tick of this entry, starting with the tick it becomes current and then every tick until it
     * reports that it is finished.
     */
    Outcome consume(TimelineContext context);

    /**
     * Whether this entry can run at all in the given context. A consumer asks this once before the run starts, so
     * an entry that cannot resolve is rejected up front instead of failing halfway.
     */
    default boolean validate(TimelineContext context) {
        return true;
    }

    enum Outcome {
        /// The entry is not done; the consumer stays on it next tick.
        RUNNING,
        /// The entry is done; the consumer advances to the next one.
        FINISHED,
        /// The entry cannot continue; the consumer ends the run as a failure.
        FAILED
    }
}
