package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record TargetCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<TargetCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(TargetCondition::new, TargetCondition::condition);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.condition.test(target, context);
    }

    @Override
    public MapCodec<TargetCondition> codec() {
        return CODEC;
    }
}
