package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record OrEntityCondition(List<EntityCondition> conditions) implements EntityCondition {
    public static final MapCodec<OrEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrEntityCondition::new, OrEntityCondition::conditions);

    public OrEntityCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity entity) {
        return this.conditions.stream().anyMatch(condition -> condition.test(entity));
    }

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return this.conditions.stream().anyMatch(condition -> condition.test(entity, context));
    }

    @Override
    public MapCodec<OrEntityCondition> codec() {
        return CODEC;
    }
}
