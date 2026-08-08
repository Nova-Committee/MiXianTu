package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Moves no more than the actor currently owns, so values never become negative.
 */
public record TransferResourceBiEntityAction(Holder<Resource> resource,
                                             NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<TransferResourceBiEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Resource.CODEC.fieldOf("resource").forGetter(TransferResourceBiEntityAction::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(TransferResourceBiEntityAction::amount)
    ).apply(instance, TransferResourceBiEntityAction::new));

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        double requested = this.amount.evaluate(context);
        if (!Double.isFinite(requested) || requested <= 0.0D) return;
        ResourceHolderData from = actor.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceHolderData to = target.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext targetContext = target instanceof LivingEntity living
                ? ResourceService.formulaContext(living, this.resource, context)
                : context;
        if (!ResourceService.change(to, this.resource, 0.0D, targetContext).valid()) return;
        Bounds bounds = ResourceService.resolveBounds(this.resource.value(), targetContext).orElse(null);
        if (bounds == null) return;
        double moved = Math.min(Math.min(from.get(this.resource), requested), Math.max(0.0D, bounds.max() - to.get(this.resource)));
        if (moved <= 0.0D) return;
        FormulaContext sourceContext = actor instanceof LivingEntity living
                ? ResourceService.formulaContext(living, this.resource, context)
                : context;
        if (!ResourceService.change(from, this.resource, -moved, sourceContext).valid()) return;
        ResourceService.change(to, this.resource, moved, targetContext);
    }

    @Override
    public MapCodec<TransferResourceBiEntityAction> codec() {
        return CODEC;
    }
}
