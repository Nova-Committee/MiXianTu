package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record ConstantCondition(boolean value) implements BiEntityCondition {
    public static final MapCodec<ConstantCondition> CODEC = Codec.BOOL.fieldOf("value").xmap(ConstantCondition::new, ConstantCondition::value);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return this.value;
    }

    @Override
    public @NonNull MapCodec<ConstantCondition> codec() {
        return CODEC;
    }
}
