package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record NotCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<NotCondition> CODEC = BiEntityCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return !this.condition.test(actor, target, context);
    }

    @Override
    public MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
