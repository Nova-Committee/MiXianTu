package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads the {@code amount} of the {@code mxt:resource} of a named ability, counting an unwritten one as 0; an
 * ability that declares no such kind is simply false.
 */
public record AbilityStorageResourceCondition(Holder<Ability> ability,
                                              Optional<NumberRange> amount) implements EntityCondition {
    public static final MapCodec<AbilityStorageResourceCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(AbilityStorageResourceCondition::ability),
            NumberRange.CODEC.optionalFieldOf("amount").forGetter(AbilityStorageResourceCondition::amount)
    ).apply(i, AbilityStorageResourceCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        AbilityStorageReading reading = AbilityStorageReading.of(ctx.entity(), this.ability);
        if (reading.declared(ResourceDataStorage.class).isEmpty()) return false;
        Optional<ResourceDataStorage> stored = reading.stored(ResourceDataStorage.class);
        return this.amount.map(numberRange -> numberRange.test(stored.flatMap(ResourceDataStorage::amount).orElse(0.0D), ctx.formula())).orElseGet(stored::isPresent);
    }

    @Override
    public @NonNull MapCodec<AbilityStorageResourceCondition> codec() {
        return CODEC;
    }
}
