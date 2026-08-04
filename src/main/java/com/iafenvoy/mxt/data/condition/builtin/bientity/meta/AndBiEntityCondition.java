package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record AndBiEntityCondition(List<BiEntityCondition> conditions) implements BiEntityCondition {
    public static final MapCodec<AndBiEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBiEntityCondition::new, AndBiEntityCondition::conditions);

    public AndBiEntityCondition {
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.conditions.stream().allMatch(condition -> condition.test(actor, target, context));
    }

    @Override
    public MapCodec<AndBiEntityCondition> codec() {
        return CODEC;
    }
}
