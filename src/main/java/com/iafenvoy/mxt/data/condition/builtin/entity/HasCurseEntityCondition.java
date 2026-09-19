package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.curse.CurseFilter;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

/**
 * Asks whether the entity holds a curse the query accepts: by definition, by tag, by stacks or by remaining time.
 */
public record HasCurseEntityCondition(CurseFilter filter) implements EntityCondition {
    public static final MapCodec<HasCurseEntityCondition> CODEC = CurseFilter.MAP_CODEC.xmap(HasCurseEntityCondition::new, HasCurseEntityCondition::filter);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return this.filter.test(ctx.entity(), ctx.formula());
    }

    @Override
    public @NonNull MapCodec<HasCurseEntityCondition> codec() {
        return CODEC;
    }
}
