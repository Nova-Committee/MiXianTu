package com.iafenvoy.mxt.data.condition.builtin.entity.meta;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AndEntityCondition(List<EntityCondition> conditions) implements EntityCondition {
    public static final MapCodec<AndEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndEntityCondition::new, AndEntityCondition::conditions);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.conditions.stream().allMatch(condition -> condition.test(entity, ctx));
    }

    @Override
    public @NonNull MapCodec<AndEntityCondition> codec() {
        return CODEC;
    }
}
