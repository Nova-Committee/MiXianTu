package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record RelativeHealthCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<RelativeHealthCondition> CODEC = Comparison.CODEC.xmap(RelativeHealthCondition::new, RelativeHealthCondition::comparison);

    @Override
    public boolean test(Entity entity) {
        return entity instanceof LivingEntity living && this.comparison.compare(living.getHealth() / living.getMaxHealth());
    }

    @Override
    public MapCodec<RelativeHealthCondition> codec() {
        return CODEC;
    }
}
