package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Moves no more than the actor currently owns, so values never become negative.
 */
public record TransferResourceBiEntityAction(Identifier resource, NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<TransferResourceBiEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("resource").forGetter(TransferResourceBiEntityAction::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(TransferResourceBiEntityAction::amount)
    ).apply(instance, TransferResourceBiEntityAction::new));

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        double requested = this.amount.evaluate(context);
        if (!Double.isFinite(requested) || requested <= 0.0D) return;
        ResourceHolderData from = actor.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceHolderData to = target.getData(MxtAttachments.RESOURCE_HOLDER);
        double moved = Math.min(from.get(this.resource), requested);
        if (moved <= 0.0D) return;
        from.set(this.resource, from.get(this.resource) - moved);
        to.set(this.resource, to.get(this.resource) + moved);
    }

    @Override
    public MapCodec<TransferResourceBiEntityAction> codec() {
        return CODEC;
    }
}
