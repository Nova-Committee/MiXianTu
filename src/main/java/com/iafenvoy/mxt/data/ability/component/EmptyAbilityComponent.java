package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.mojang.serialization.MapCodec;

public enum EmptyAbilityComponent implements AbilityComponent {
    INSTANCE;
    public static final MapCodec<EmptyAbilityComponent> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyAbilityComponent> codec() {
        return CODEC;
    }
}
