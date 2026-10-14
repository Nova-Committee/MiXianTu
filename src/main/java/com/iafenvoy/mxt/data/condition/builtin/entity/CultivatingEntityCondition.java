package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
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
        CultivationAttachment spirit = ctx.entity().getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (spirit == null || !spirit.cultivating()) return false;
        return this.action.map(cultivationHolder -> spirit.cultivation().map(cultivationHolder::equals).orElse(false)).orElse(true);
    }

    @Override
    public @NonNull MapCodec<CultivatingEntityCondition> codec() {
        return CODEC;
    }
}
