package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Adds a finite signed amount to a server-owned resource attachment.
 */
public record AddResourceEntityAction(Identifier resource, NumberProvider amount) implements EntityAction {
    public static final MapCodec<AddResourceEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("resource").forGetter(AddResourceEntityAction::resource),
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
        MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, this.resource).ifPresent(definition -> ResourceService.change(resources, this.resource, definition, amount, context));
    }

    @Override
    public MapCodec<AddResourceEntityAction> codec() {
        return CODEC;
    }
}
