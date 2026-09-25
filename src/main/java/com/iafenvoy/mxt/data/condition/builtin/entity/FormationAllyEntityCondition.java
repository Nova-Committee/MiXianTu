package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.runtime.formation.FormationCarrier;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Whether any of the formation's owners treats the entity as his own, read from the carrier the ticker already
 * hands to every per-entity action. Answers {@code false} outside a formation context or with no owner.
 */
public enum FormationAllyEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationAllyEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormationCarrier carrier = FormationCarrier.of(ctx).orElse(null);
        if (carrier == null) return false;
        return carrier.owners().identify(entity.level(), entity) == TriState.TRUE;
    }

    @Override
    public @NonNull MapCodec<FormationAllyEntityCondition> codec() {
        return CODEC;
    }
}
