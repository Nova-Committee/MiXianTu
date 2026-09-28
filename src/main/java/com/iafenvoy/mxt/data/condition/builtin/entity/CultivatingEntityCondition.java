package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Asks whether the entity is cultivating, optionally inside one named method. The state is persisted before the
 * first tick, so the answer is the same for the whole run rather than only after a settlement.
 */
public record CultivatingEntityCondition(Optional<Holder<Cultivation>> action) implements EntityCondition {
    public static final MapCodec<CultivatingEntityCondition> CODEC = Cultivation.CODEC.optionalFieldOf("action")
            .xmap(CultivatingEntityCondition::new, CultivatingEntityCondition::action);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        CultivationAttachment spirit = entity.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (spirit == null || !spirit.cultivating()) return false;
        if (this.action.isEmpty()) return true;
        // Asked by id: the stored holder and a freshly decoded one are not the same instance.
        Identifier asked = HolderHelper.id(this.action.get());
        return spirit.cultivation().map(running -> HolderHelper.id(running).equals(asked)).orElse(false);
    }

    @Override
    public @NonNull MapCodec<CultivatingEntityCondition> codec() {
        return CODEC;
    }
}
