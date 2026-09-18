package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Runs one entity action and finishes on the tick it runs, so a timeline can place a beat anywhere. It has no
 * fixed length, which is what makes several of these in a row resolve in a single tick.
 */
public record ActionEntry(EntityAction action) implements TimelineEntry {
    public static final MapCodec<ActionEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.CODEC.fieldOf("action").forGetter(ActionEntry::action)
    ).apply(i, ActionEntry::new));

    @Override
    public MapCodec<ActionEntry> codec() {
        return CODEC;
    }

    @Override
    public Outcome consume(TimelineContext context) {
        this.action.execute(context.entity(), context.formula());
        return Outcome.FINISHED;
    }
}
