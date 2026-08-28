package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Treats the owner of any active formation in the current level as its member.
 */
public enum FormationMemberEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationMemberEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity.level().getData(MxtAttachments.FORMATION_WORLD).formations().values().stream()
                .anyMatch(formation -> formation.active() && formation.owner().filter(entity.getUUID()::equals).isPresent());
    }

    @Override
    public @NonNull MapCodec<FormationMemberEntityCondition> codec() {
        return CODEC;
    }
}
