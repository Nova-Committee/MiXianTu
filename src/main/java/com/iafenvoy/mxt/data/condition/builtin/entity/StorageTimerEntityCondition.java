package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.TimerDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads how much of the {@code mxt:timer} of a named host is left as ticks until its stored {@code ends_at}, and
 * whether that end has passed. A timer with no {@code ends_at} is not running, so it has no time left and counts
 * as ended, and an elapsed timer has no time left either. A host that does not declare that kind is simply false.
 */
public record StorageTimerEntityCondition(Identifier family, Identifier id, Optional<NumberRange> remaining,
                                          Optional<Boolean> ended) implements EntityCondition {
    public static final MapCodec<StorageTimerEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageTimerEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageTimerEntityCondition::id),
            NumberRange.CODEC.optionalFieldOf("remaining").forGetter(StorageTimerEntityCondition::remaining),
            Codec.BOOL.optionalFieldOf("ended").forGetter(StorageTimerEntityCondition::ended)
    ).apply(i, StorageTimerEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        if (reading.declared(TimerDataStorage.class).isEmpty()) return false;
        Optional<Double> endsAt = reading.stored(TimerDataStorage.class).flatMap(TimerDataStorage::endsAt);
        long gameTime = ctx.entity().level().getGameTime();
        // "Ticks until the end" cannot be negative: an ended timer is out of time, exactly like one that never ran.
        double left = endsAt.map(end -> Math.max(0.0D, end - gameTime)).orElse(0.0D);
        boolean finished = endsAt.map(end -> end <= gameTime).orElse(true);
        if (this.remaining.isPresent() && !this.remaining.get().test(left, ctx.formula())) return false;
        return this.ended.isEmpty() || this.ended.get() == finished;
    }

    @Override
    public @NonNull MapCodec<StorageTimerEntityCondition> codec() {
        return CODEC;
    }
}
