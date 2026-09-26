package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.runtime.formation.FormationCarrier;
import com.iafenvoy.mxt.runtime.formation.FormationRelations;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Whether the entity owns the formation currently being evaluated; {@code false} outside a formation context.
 * Not to be conflated with {@link FormationMemberEntityCondition}, which asks about any formation in the level.
 */
public enum FormationOwnerEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationOwnerEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return FormationCarrier.of(ctx)
                .map(carrier -> FormationRelations.isOwner(carrier, ctx.entity()))
                .orElse(false);
    }

    @Override
    public @NonNull MapCodec<FormationOwnerEntityCondition> codec() {
        return CODEC;
    }
}
