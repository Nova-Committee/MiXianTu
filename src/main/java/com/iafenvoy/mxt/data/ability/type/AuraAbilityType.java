package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AuraAbilityType(NumberProvider interval, NumberProvider radius) implements AbilityType {
    public static final MapCodec<AuraAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(20.0D)).forGetter(AuraAbilityType::interval),
            NumberProvider.CODEC.optionalFieldOf("radius", new Constant(4.0D)).forGetter(AuraAbilityType::radius)
    ).apply(i, AuraAbilityType::new));

    @Override
    public MapCodec<AuraAbilityType> codec() {
        return CODEC;
    }
}
