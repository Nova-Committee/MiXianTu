package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record OrCondition(List<BiEntityCondition> conditions) implements BiEntityCondition {
    public static final MapCodec<OrCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrCondition::new, OrCondition::conditions);

    public OrCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.conditions.stream().anyMatch(condition -> condition.test(actor, target, context));
    }

    @Override
    public MapCodec<OrCondition> codec() {
        return CODEC;
    }
}
