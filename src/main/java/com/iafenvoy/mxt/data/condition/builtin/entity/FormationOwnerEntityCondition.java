package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.runtime.formation.FormationCarrier;
import com.iafenvoy.mxt.runtime.formation.FormationRelations;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Whether the entity owns the formation currently being evaluated.
 *
 * <p>Deliberately narrow: it answers "is this <em>the</em> owner of <em>this</em> formation".
 * {@link FormationMemberEntityCondition} answers a different question — whether the entity owns
 * <em>any</em> active formation in the level — and the two must not be conflated.</p>
 *
 * <p>Outside a formation context the answer is {@code false}, not "owns nothing" or "owns
 * anything": a skill or an item-binding condition simply has no formation to ask about.</p>
 */
public enum FormationOwnerEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationOwnerEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return FormationCarrier.of(ctx)
                .map(carrier -> FormationRelations.isOwner(carrier, entity))
                .orElse(false);
    }

    @Override
    public @NonNull MapCodec<FormationOwnerEntityCondition> codec() {
        return CODEC;
    }
}
