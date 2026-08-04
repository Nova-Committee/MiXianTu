package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record BothCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<BothCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(BothCondition::new, BothCondition::condition);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.condition.test(actor, context) && this.condition.test(target, context);
    }

    @Override
    public MapCodec<BothCondition> codec() {
        return CODEC;
    }
}
