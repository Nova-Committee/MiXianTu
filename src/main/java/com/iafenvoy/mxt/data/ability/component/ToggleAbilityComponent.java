package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record ToggleAbilityComponent(boolean defaultValue) implements AbilityComponent {
    public static final MapCodec<ToggleAbilityComponent> CODEC = Codec.BOOL.optionalFieldOf("default", false).xmap(ToggleAbilityComponent::new, ToggleAbilityComponent::defaultValue);

    @Override
    public MapCodec<ToggleAbilityComponent> codec() {
        return CODEC;
    }
}
