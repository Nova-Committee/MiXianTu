package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record ChannelledAbilityType(NumberProvider tickInterval,
                                    List<ResourceCost> upkeepCosts) implements AbilityType {
    public static final MapCodec<ChannelledAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(1.0D)).forGetter(ChannelledAbilityType::tickInterval),
            ResourceCost.LIST_CODEC.optionalFieldOf("upkeep_costs", List.of()).forGetter(ChannelledAbilityType::upkeepCosts)
    ).apply(i, ChannelledAbilityType::new));

    @Override
    public MapCodec<ChannelledAbilityType> codec() {
        return CODEC;
    }
}
