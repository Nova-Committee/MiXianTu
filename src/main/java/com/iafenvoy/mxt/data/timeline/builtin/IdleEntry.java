package com.iafenvoy.mxt.data.timeline.builtin;

import com.iafenvoy.mxt.data.storage.runtime.IdleCountdown;
import com.iafenvoy.mxt.data.timeline.TimelineContext;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Does nothing for a fixed number of ticks, which is how a timeline holds a beat open. The beat counts itself down
 * in the run's state, so the length it was given when it began is what it waits out.
 */
public record IdleEntry(NumberProvider duration) implements TimelineEntry {
    public static final MapCodec<IdleEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("duration").forGetter(IdleEntry::duration)
    ).apply(i, IdleEntry::new));

    @Override
    public MapCodec<IdleEntry> codec() {
        return CODEC;
    }

    @Override
    public void begin(TimelineContext context) {
        context.state().set(new IdleCountdown(context.ticks(this.duration)));
    }

    @Override
    public Outcome consume(TimelineContext context) {
        IdleCountdown countdown = context.state().get(IdleCountdown.class).orElse(null);
        // A missing or non-positive countdown is a length that could not be resolved when the beat began. That is
        // the failure validate() cannot report on its own: it answers with a non-positive length for a beat that
        // decides per tick, and this one never does.
        if (countdown == null || countdown.remaining() <= 0L) return Outcome.FAILED;
        if (countdown.remaining() == 1L) return Outcome.FINISHED;
        countdown.set(countdown.remaining() - 1L);
        return Outcome.RUNNING;
    }

    @Override
    public boolean validate(TimelineContext context) {
        return context.ticks(this.duration) >= 0L;
    }
}
