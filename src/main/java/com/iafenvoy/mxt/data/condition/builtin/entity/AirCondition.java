package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record AirCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AirCondition> CODEC = Comparison.CODEC.xmap(AirCondition::new, AirCondition::comparison);

    @Override
    public boolean test(Entity entity) {
        return this.comparison.compare(entity.getAirSupply());
    }

    @Override
    public MapCodec<AirCondition> codec() {
        return CODEC;
    }
}
