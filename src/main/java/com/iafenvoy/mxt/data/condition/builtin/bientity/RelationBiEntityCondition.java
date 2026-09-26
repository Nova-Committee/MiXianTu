package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Relationship predicate kept distinct from the team predicate: each builtin registration needs its own codec value.
 */
public record RelationBiEntityCondition(boolean allied) implements BiEntityCondition {
    public static final MapCodec<RelationBiEntityCondition> CODEC = Codec.BOOL.optionalFieldOf("allied", true)
            .xmap(RelationBiEntityCondition::new, RelationBiEntityCondition::allied);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        return ctx.actor().isAlliedTo(ctx.target()) == this.allied;
    }

    @Override
    public @NonNull MapCodec<RelationBiEntityCondition> codec() {
        return CODEC;
    }
}
