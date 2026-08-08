package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

public record TimerAbilityComponent(NumberProvider duration) implements AbilityComponent {
    public static final MapCodec<TimerAbilityComponent> CODEC = NumberProvider.CODEC.fieldOf("duration").xmap(TimerAbilityComponent::new, TimerAbilityComponent::duration);

    @Override
    public MapCodec<TimerAbilityComponent> codec() {
        return CODEC;
    }
}
