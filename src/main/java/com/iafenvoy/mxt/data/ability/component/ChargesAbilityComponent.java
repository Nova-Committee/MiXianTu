package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ChargesAbilityComponent(NumberProvider maximum,
                                      NumberProvider rechargeTicks) implements AbilityComponent {
    public static final MapCodec<ChargesAbilityComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("maximum").forGetter(ChargesAbilityComponent::maximum),
            NumberProvider.CODEC.fieldOf("recharge_ticks").forGetter(ChargesAbilityComponent::rechargeTicks)
    ).apply(i, ChargesAbilityComponent::new));

    @Override
    public MapCodec<ChargesAbilityComponent> codec() {
        return CODEC;
    }
}
