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
 * Whether the entity is somebody the owner of the formation being evaluated treats as his own.
 *
 * <p>This exists because the per-entity actions of a formation are {@link
 * com.iafenvoy.mxt.data.action.EntityAction}s, and their condition slot is an {@link EntityCondition}: a
 * two-entity condition has nothing to pair the subject with there. The missing half is the formation's
 * owner, and the formation itself is the only place that is known, so the condition reads the carrier the
 * ticker already hands to every per-entity action.</p>
 *
 * <p>What it buys over the formation-wide {@code hostile} flag is one judgement per action rather than one
 * per formation: a pack can hurt strangers in one branch and help friends in another, which a single flag
 * cannot express.</p>
 *
 * <p>Answers {@code false} outside a formation context and for a formation that records no owner. There is
 * then nobody to be a friend of, and "not an ally" is the reading that keeps a
 * {@code not(mxt:formation_ally)} guard on the damaging branch behaving as written rather than silently
 * letting everyone through.</p>
 *
 * <p>An owner who is merely offline is a different case: the judgement is asked by id, so a source that
 * keeps its own per-player data can still answer, and only "nobody can identify this entity" falls back to
 * {@code false}. That is the same pipeline the formation-wide {@code hostile} flag uses, which is what keeps
 * the two ways of asking the question from disagreeing.</p>
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
