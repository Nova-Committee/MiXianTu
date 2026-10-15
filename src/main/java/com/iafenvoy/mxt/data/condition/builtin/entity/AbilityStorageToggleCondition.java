package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads the {@code mxt:toggle} state of a named ability: the stored {@code state}, or the declaration's
 * {@code default} while nothing was written. An ability that declares no such kind is false.
 */
public record AbilityStorageToggleCondition(Holder<Ability> ability,
                                            boolean expected) implements EntityCondition {
    public static final MapCodec<AbilityStorageToggleCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(AbilityStorageToggleCondition::ability),
            Codec.BOOL.optionalFieldOf("expected", true).forGetter(AbilityStorageToggleCondition::expected)
    ).apply(i, AbilityStorageToggleCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        AbilityStorageReading reading = AbilityStorageReading.of(ctx.entity(), this.ability);
        Optional<ToggleDataStorage> declared = reading.declared(ToggleDataStorage.class);
        if (declared.isEmpty()) return false;
        boolean state = reading.stored(ToggleDataStorage.class).flatMap(ToggleDataStorage::state)
                .orElse(declared.get().defaultValue());
        return state == this.expected;
    }

    @Override
    public @NonNull MapCodec<AbilityStorageToggleCondition> codec() {
        return CODEC;
    }
}
