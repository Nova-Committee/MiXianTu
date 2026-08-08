package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

public record CooldownAbilityComponent(NumberProvider ticks) implements AbilityComponent {
    public static final MapCodec<CooldownAbilityComponent> CODEC = NumberProvider.CODEC.fieldOf("ticks").xmap(CooldownAbilityComponent::new, CooldownAbilityComponent::ticks);

    @Override
    public MapCodec<CooldownAbilityComponent> codec() {
        return CODEC;
    }
}
