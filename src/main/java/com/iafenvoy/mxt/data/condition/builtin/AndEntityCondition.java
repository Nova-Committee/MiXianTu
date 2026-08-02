package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record AndEntityCondition(List<EntityCondition> conditions) implements EntityCondition {
    public static final MapCodec<AndEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndEntityCondition::new, AndEntityCondition::conditions);

    public AndEntityCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity entity) {
        return this.conditions.stream().allMatch(condition -> condition.test(entity));
    }

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return this.conditions.stream().allMatch(condition -> condition.test(entity, context));
    }

    @Override
    public MapCodec<AndEntityCondition> codec() {
        return CODEC;
    }
}
