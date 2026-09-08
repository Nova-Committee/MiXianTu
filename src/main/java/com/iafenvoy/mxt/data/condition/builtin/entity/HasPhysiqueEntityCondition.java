package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * True when the entity holds the configured physique.
 */
public record HasPhysiqueEntityCondition(Holder<Physique> physique) implements EntityCondition {
    public static final MapCodec<HasPhysiqueEntityCondition> CODEC = Physique.CODEC.fieldOf("physique").xmap(HasPhysiqueEntityCondition::new, HasPhysiqueEntityCondition::physique);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().getData(MxtAttachments.SPIRIT_IDENTITY).physiques().contains(this.physique);
    }

    @Override
    public @NonNull MapCodec<HasPhysiqueEntityCondition> codec() {
        return CODEC;
    }
}
