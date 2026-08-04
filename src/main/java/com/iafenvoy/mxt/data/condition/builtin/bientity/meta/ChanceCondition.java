package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record ChanceCondition(double chance) implements BiEntityCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return actor.getRandom().nextDouble() < this.chance;
    }

    @Override
    public MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
