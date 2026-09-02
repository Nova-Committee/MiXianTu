package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record HasCurseEntityCondition(Holder<Curse> curse) implements EntityCondition {
    public static final MapCodec<HasCurseEntityCondition> CODEC = Curse.CODEC.fieldOf("curse").xmap(HasCurseEntityCondition::new, HasCurseEntityCondition::curse);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(this.curse);
    }

    @Override
    public @NonNull MapCodec<HasCurseEntityCondition> codec() {
        return CODEC;
    }
}
