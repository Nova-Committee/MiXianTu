package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Whether the entity owns <em>any</em> registered formation in the current level.
 *
 * <p>"Registered" is the whole of it: a formation exists while it holds an entry in the level's
 * formation index, and there is no separate active flag to consult — every reader, including the
 * ticker, treats membership that way.</p>
 *
 * <p>This is a level-wide question, not a per-formation one: it takes no arguments and does not
 * know which formation is being evaluated. For "is this entity the owner of the formation we are
 * currently inside", use {@code mxt:formation_owner} instead.</p>
 *
 * <p>The name says "member" while the behaviour says "owner", which is why the two conditions are
 * easy to confuse. Renaming this id would break existing data packs, so the behaviour is documented
 * here rather than changed.</p>
 */
public enum FormationMemberEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationMemberEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return entity.level().getData(MxtAttachments.FORMATION_WORLD).formations().values().stream()
                .anyMatch(formation -> formation.owner().filter(entity.getUUID()::equals).isPresent());
    }

    @Override
    public @NonNull MapCodec<FormationMemberEntityCondition> codec() {
        return CODEC;
    }
}
