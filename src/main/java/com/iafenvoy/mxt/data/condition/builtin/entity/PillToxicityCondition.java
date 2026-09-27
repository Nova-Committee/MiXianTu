package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Current pill toxicity. An entity that has never taken a pill reads 0 and does not gain an attachment.
 */
public record PillToxicityCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<PillToxicityCondition> CODEC = Comparison.CODEC.xmap(PillToxicityCondition::new, PillToxicityCondition::comparison);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && this.comparison.compare(PillService.toxicity(living));
    }

    @Override
    public @NonNull MapCodec<PillToxicityCondition> codec() {
        return CODEC;
    }
}
