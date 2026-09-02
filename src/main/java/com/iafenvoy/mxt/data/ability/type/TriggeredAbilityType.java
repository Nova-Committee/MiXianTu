package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record TriggeredAbilityType(List<Trigger> triggers, NumberProvider chance) implements AbilityType {
    public static final MapCodec<TriggeredAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(TriggeredAbilityType::triggers),
            NumberProvider.CODEC.optionalFieldOf("chance", new Constant(1.0D)).forGetter(TriggeredAbilityType::chance)
    ).apply(i, TriggeredAbilityType::new));

    @Override
    public MapCodec<TriggeredAbilityType> codec() {
        return CODEC;
    }
}
