package com.iafenvoy.mxt.data.timeline.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.timeline.TimelineContext;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.OptionalInt;

/**
 * Chooses where the timeline goes next instead of always moving on by one, which is how a run is split into
 * branches: the condition is read once, and the beat finishes on the tick it runs. A missing target means the next
 * beat, and an index is counted from the first beat of the timeline the run copied in, so the two branches read
 * the same whichever index the run reached this beat from.
 */
public record BranchEntry(EntityCondition condition, OptionalInt ifTrue,
                          OptionalInt ifFalse) implements TimelineEntry {
    public static final MapCodec<BranchEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(BranchEntry::condition),
            OptionalCodecs.intCodec(0, 4096, "if_true").forGetter(BranchEntry::ifTrue),
            OptionalCodecs.intCodec(0, 4096, "if_false").forGetter(BranchEntry::ifFalse)
    ).apply(i, BranchEntry::new));

    @Override
    public MapCodec<BranchEntry> codec() {
        return CODEC;
    }

    @Override
    public Outcome consume(TimelineContext context) {
        OptionalInt target = this.condition.test(context.entity(), context.formula())
                ? this.ifTrue : this.ifFalse;
        target.ifPresent(context.jump()::to);
        return Outcome.FINISHED;
    }

    // Asked once before the run starts: a target past the end of the timeline is a content error, and the only
    // moment it can be refused rather than stranding a run that already paid for its breakthrough.
    @Override
    public boolean validate(TimelineContext context) {
        return inRange(this.ifTrue, context.length()) && inRange(this.ifFalse, context.length());
    }

    private static boolean inRange(OptionalInt target, int length) {
        return target.isEmpty() || target.getAsInt() < length;
    }
}
