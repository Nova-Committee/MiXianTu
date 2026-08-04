package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record ActorCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<ActorCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(ActorCondition::new, ActorCondition::condition);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.condition.test(actor, context);
    }

    @Override
    public MapCodec<ActorCondition> codec() {
        return CODEC;
    }
}
