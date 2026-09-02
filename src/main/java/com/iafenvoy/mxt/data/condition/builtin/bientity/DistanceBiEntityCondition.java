package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record DistanceBiEntityCondition(NumberProvider maximum) implements BiEntityCondition {
    public static final MapCodec<DistanceBiEntityCondition> CODEC = NumberProvider.CODEC.fieldOf("maximum").xmap(DistanceBiEntityCondition::new, DistanceBiEntityCondition::maximum);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        double maximum = this.maximum.evaluate(context);
        return Double.isFinite(maximum) && maximum >= 0.0D && actor.distanceToSqr(target) <= maximum * maximum;
    }

    @Override
    public @NonNull MapCodec<DistanceBiEntityCondition> codec() {
        return CODEC;
    }
}
