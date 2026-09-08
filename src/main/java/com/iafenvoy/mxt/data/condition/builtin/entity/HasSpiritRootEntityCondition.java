package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * True when the entity holds the configured spirit root.
 */
public record HasSpiritRootEntityCondition(Holder<SpiritRoot> spiritRoot) implements EntityCondition {
    public static final MapCodec<HasSpiritRootEntityCondition> CODEC = SpiritRoot.CODEC.fieldOf("spirit_root").xmap(HasSpiritRootEntityCondition::new, HasSpiritRootEntityCondition::spiritRoot);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().contains(this.spiritRoot);
    }

    @Override
    public @NonNull MapCodec<HasSpiritRootEntityCondition> codec() {
        return CODEC;
    }
}
