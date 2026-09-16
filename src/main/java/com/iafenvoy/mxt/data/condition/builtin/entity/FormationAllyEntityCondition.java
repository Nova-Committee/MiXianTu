package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.runtime.formation.FormationCarrier;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Whether the entity is somebody the owner of the formation being evaluated treats as his own. It reads the
 * carrier the ticker already hands to every per-entity action, because a formation's per-entity actions take an
 * {@link EntityCondition}, which has no second entity to pair with the formation's owner. Answers {@code false}
 * outside a formation context, with no owner, and for an entity nobody can identify.
 */
public enum FormationAllyEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<FormationAllyEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormationCarrier carrier = FormationCarrier.of(ctx).orElse(null);
        if (carrier == null) return false;
        UUID ownerId = carrier.owner().orElse(null);
        if (ownerId == null) return false;
        return FriendService.identify(ownerId, entity.level().getEntity(ownerId), entity) == TriState.TRUE;
    }

    @Override
    public @NonNull MapCodec<FormationAllyEntityCondition> codec() {
        return CODEC;
    }
}
