package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record ChannelledAbilityType(NumberProvider tickInterval,
                                    List<Cost> upkeepCosts) implements AbilityType, Togglable {
    public static final MapCodec<ChannelledAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(1.0D)).forGetter(ChannelledAbilityType::tickInterval),
            Cost.LIST_CODEC.optionalFieldOf("upkeep_costs", List.of()).forGetter(ChannelledAbilityType::upkeepCosts)
    ).apply(i, ChannelledAbilityType::new));

    @Override
    public MapCodec<ChannelledAbilityType> codec() {
        return CODEC;
    }

    // Starting a channel is a cast; the upkeep pulses are paid where they are taken.
    @Override
    public boolean gated(ToggleContext context) {
        return false;
    }

    @Override
    public Result activate(ToggleContext context) {
        return AbilityActivationService.cast(context);
    }
}
