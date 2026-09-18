package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Waits until a condition holds, re-read every tick. A condition that never holds parks the run on this
 * entry, which is the point: the timeline waits for the world instead of guessing a duration.
 */
public record WaitForEntry(EntityCondition condition) implements TimelineEntry {
    public static final MapCodec<WaitForEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(WaitForEntry::condition)
    ).apply(i, WaitForEntry::new));

    @Override
    public MapCodec<WaitForEntry> codec() {
        return CODEC;
    }

    @Override
    public Outcome consume(TimelineContext context) {
        return this.condition.test(context.entity(), context.formula()) ? Outcome.FINISHED : Outcome.RUNNING;
    }
}
