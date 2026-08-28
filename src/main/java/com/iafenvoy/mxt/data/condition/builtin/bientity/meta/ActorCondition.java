package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record ActorCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<ActorCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(ActorCondition::new, ActorCondition::condition);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return this.condition.test(actor, ctx);
    }

    @Override
    public @NonNull MapCodec<ActorCondition> codec() {
        return CODEC;
    }
}
