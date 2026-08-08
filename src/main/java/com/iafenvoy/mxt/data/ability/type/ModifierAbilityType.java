package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ModifierAbilityType(List<Identifier> queryTags) implements AbilityType {
    public static final MapCodec<ModifierAbilityType> CODEC = Identifier.CODEC.listOf().optionalFieldOf("query_tags", List.of()).xmap(ModifierAbilityType::new, ModifierAbilityType::queryTags);

    @Override
    public MapCodec<ModifierAbilityType> codec() {
        return CODEC;
    }
}
