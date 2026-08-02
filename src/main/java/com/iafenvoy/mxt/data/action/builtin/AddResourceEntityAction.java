package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

/**
 * Adds a finite signed amount to a server-owned resource attachment.
 */
public record AddResourceEntityAction(Holder<ResourceDefinition> resource,
                                      NumberProvider amount) implements EntityAction {
    public static final MapCodec<AddResourceEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceDefinition.HOLDER_CODEC.fieldOf("resource").forGetter(AddResourceEntityAction::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AddResourceEntityAction::amount)
    ).apply(instance, AddResourceEntityAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount)) return;
        ResourceHolderData resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceService.change(resources, HolderHelper.id(this.resource), this.resource.value(), amount, context);
    }

    @Override
    public MapCodec<AddResourceEntityAction> codec() {
        return CODEC;
    }
}
