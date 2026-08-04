package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record AttackCooldownCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AttackCooldownCondition> CODEC = Comparison.CODEC.xmap(AttackCooldownCondition::new, AttackCooldownCondition::comparison);

    @Override
    public boolean test(Entity entity) {
        return entity instanceof Player player && this.comparison.compare(player.getAttackStrengthScale(0.0F));
    }

    @Override
    public MapCodec<AttackCooldownCondition> codec() {
        return CODEC;
    }
}
