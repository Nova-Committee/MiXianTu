package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Code-owned ability behaviour selected by a datapack {@code type}. Concrete spells remain
 * named datapack entries; this type only selects their lifecycle algorithm.
 */
public interface AbilityType {
    MapCodec<AbilityType> CODEC = MxtRegistries.ABILITY_TYPE.byNameCodec().dispatchMap("type", AbilityType::codec, Function.identity());

    MapCodec<? extends AbilityType> codec();
}
