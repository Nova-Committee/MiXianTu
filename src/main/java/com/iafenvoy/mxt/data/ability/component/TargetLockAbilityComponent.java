package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

public record TargetLockAbilityComponent(NumberProvider range) implements AbilityComponent {
    public static final MapCodec<TargetLockAbilityComponent> CODEC = NumberProvider.CODEC.fieldOf("range").xmap(TargetLockAbilityComponent::new, TargetLockAbilityComponent::range);

    @Override
    public MapCodec<TargetLockAbilityComponent> codec() {
        return CODEC;
    }
}
