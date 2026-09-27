package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A passive ability: its {@code modifiers} apply for as long as the ability is granted, and its {@code condition}
 * is re-evaluated every tick, so a passive can be gated on the holder's state the way an aura is.
 */
public record ModifierAbilityType(List<AttributeEntry> modifiers) implements AbilityType {
    public static final MapCodec<ModifierAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AttributeEntry.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(ModifierAbilityType::modifiers)
    ).apply(i, ModifierAbilityType::new));

    @Override
    public MapCodec<ModifierAbilityType> codec() {
        return CODEC;
    }
}
