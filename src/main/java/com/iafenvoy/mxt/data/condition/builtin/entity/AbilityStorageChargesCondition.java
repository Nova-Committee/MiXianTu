package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Reads how many uses the {@code mxt:charges} of a named ability have left. An ability that was never spent keeps no
 * count and reads as full (the declaration's {@code maximum}); one that declares no charge pool is false.
 */
public record AbilityStorageChargesCondition(Holder<Ability> ability,
                                             Optional<NumberRange> remaining) implements EntityCondition {
    public static final MapCodec<AbilityStorageChargesCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(AbilityStorageChargesCondition::ability),
            NumberRange.CODEC.optionalFieldOf("remaining").forGetter(AbilityStorageChargesCondition::remaining)
    ).apply(i, AbilityStorageChargesCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        AbilityStorageReading reading = AbilityStorageReading.of(ctx.entity(), this.ability);
        ChargesDataStorage declaration = reading.declared(ChargesDataStorage.class).orElse(null);
        if (declaration == null) return false;
        double maximum = declaration.maximum().evaluate(ctx.formula());
        OptionalDouble stored = reading.stored(ChargesDataStorage.class).map(ChargesDataStorage::remaining).orElseGet(OptionalDouble::empty);
        double left = stored.orElse(maximum);
        if (!Double.isFinite(maximum) || !Double.isFinite(left)) return false;
        return this.remaining.isEmpty() || this.remaining.get().test(left, ctx.formula());
    }

    @Override
    public @NonNull MapCodec<AbilityStorageChargesCondition> codec() {
        return CODEC;
    }
}
