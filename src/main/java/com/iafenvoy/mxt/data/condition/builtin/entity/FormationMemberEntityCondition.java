package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Whether the entity owns <em>any</em> formation in the current level; a formation "exists" while it holds an
 * entry in the level's formation index. The id says "member" because renaming it would break existing data packs.
 */
public enum FormationMemberEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationMemberEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return entity.level().getData(MxtAttachments.FORMATION_WORLD).formations().values().stream()
                .anyMatch(formation -> formation.owners().contains(entity));
    }

    @Override
    public @NonNull MapCodec<FormationMemberEntityCondition> codec() {
        return CODEC;
    }
}
