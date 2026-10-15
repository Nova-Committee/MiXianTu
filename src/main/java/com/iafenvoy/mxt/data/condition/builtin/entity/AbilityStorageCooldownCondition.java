package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.util.OptionalBoolean;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads how much of the {@code mxt:cooldown} of a named ability is left, anchored at the tick the value was written -
 * the same anchor the runtime reads. Nothing written means nothing is running: ready, with no time left.
 *
 * <p>An ability does not have to declare {@code mxt:cooldown} for this to answer: every payment writes the value and
 * its length, so a pack that states a cooldown once in the ability's own field can read it back here.
 */
public record AbilityStorageCooldownCondition(Holder<Ability> ability, Optional<NumberRange> remaining,
                                              OptionalBoolean ready) implements EntityCondition {
    public static final MapCodec<AbilityStorageCooldownCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(AbilityStorageCooldownCondition::ability),
            NumberRange.CODEC.optionalFieldOf("remaining").forGetter(AbilityStorageCooldownCondition::remaining),
            OptionalCodecs.boolCodec("ready").forGetter(AbilityStorageCooldownCondition::ready)
    ).apply(i, AbilityStorageCooldownCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        AbilityStorageReading reading = AbilityStorageReading.of(ctx.entity(), this.ability);
        double left = 0.0D;
        Optional<CooldownDataStorage> stored = reading.stored(CooldownDataStorage.class);
        // A value written without a length is a cooldown of no length, which is how a pack states "ready at once".
        if (stored.isPresent()) {
            double length = stored.get().duration().orElse(0.0D);
            if (!Double.isFinite(length)) return false;
            left = stored.get().remaining(ctx.entity().level().getGameTime());
        }
        if (this.remaining.isPresent() && !this.remaining.get().test(left, ctx.formula())) return false;
        return this.ready.isEmpty() || this.ready.getAsBoolean() == (left <= 0.0D);
    }

    @Override
    public @NonNull MapCodec<AbilityStorageCooldownCondition> codec() {
        return CODEC;
    }
}
