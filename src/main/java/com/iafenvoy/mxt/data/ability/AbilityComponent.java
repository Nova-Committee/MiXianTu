package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Declarative, code-owned state component attached to one granted ability.
 */
public interface AbilityComponent {
    Codec<AbilityComponent> CODEC = MxtTypeRegistries.ABILITY_COMPONENT_TYPE.byNameCodec().dispatch("type", AbilityComponent::codec, Function.identity());

    MapCodec<? extends AbilityComponent> codec();
}
