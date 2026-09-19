package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.mojang.serialization.MapCodec;

/**
 * A passive ability: its {@code modifiers} apply for as long as the ability is granted, and its {@code condition}
 * is re-evaluated every tick, so a passive can be gated on the holder's state the way an {@code aura} is.
 */
public enum ModifierAbilityType implements AbilityType {
    INSTANCE;
    public static final MapCodec<ModifierAbilityType> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<ModifierAbilityType> codec() {
        return CODEC;
    }
}
