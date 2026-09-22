package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One beat of a timeline, selected by a datapack {@code type}; {@link #consume(TimelineContext)} is called every
 * tick while the entry is current, so the entry itself decides whether it is instantaneous or a wait. An entry
 * keeps nothing across ticks (a timeline is copied out of a definition and shared by every run of it): numbers
 * that must survive a tick go into {@link TimelineContext#state()}.
 */
public interface TimelineEntry {
    // A codec rather than a map codec, because a timeline is a list of these and only a codec can be listed.
    Codec<TimelineEntry> CODEC = MxtRegistries.TIMELINE_ENTRY_TYPE.byNameCodec()
            .dispatch("type", TimelineEntry::codec, Function.identity());

    MapCodec<? extends TimelineEntry> codec();

    // Called once, on the tick this entry becomes the current one, with an empty state. A length resolved here is
    // settled for the rest of the beat: a later aura change cannot move a wait already running.
    default void begin(TimelineContext context) {
    }

    Outcome consume(TimelineContext context);

    // Asked once before the run starts, so an entry that cannot resolve is rejected up front rather than midway.
    default boolean validate(TimelineContext context) {
        return true;
    }

    enum Outcome {
        RUNNING,
        FINISHED,
        FAILED
    }
}
