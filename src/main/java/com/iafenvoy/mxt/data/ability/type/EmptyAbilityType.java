package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.mojang.serialization.MapCodec;

public enum EmptyAbilityType implements AbilityType {
    INSTANCE;
    public static final MapCodec<EmptyAbilityType> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyAbilityType> codec() {
        return CODEC;
    }
}
