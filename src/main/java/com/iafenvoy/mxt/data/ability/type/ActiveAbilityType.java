package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record ActiveAbilityType(String slot) implements AbilityType {
    public static final MapCodec<ActiveAbilityType> CODEC = Codec.STRING.optionalFieldOf("slot", "primary").xmap(ActiveAbilityType::new, ActiveAbilityType::slot);

    public ActiveAbilityType {
        if (slot.isBlank()) throw new IllegalArgumentException("Ability slot cannot be blank");
    }

    @Override
    public MapCodec<ActiveAbilityType> codec() {
        return CODEC;
    }
}
