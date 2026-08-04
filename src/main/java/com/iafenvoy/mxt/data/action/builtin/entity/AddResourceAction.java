package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Adds a finite signed amount to a server-owned resource attachment.
 */
public record AddResourceAction(Holder<Resource> resource,
                                NumberProvider amount) implements EntityAction {
    public static final MapCodec<AddResourceAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Resource.CODEC.fieldOf("resource").forGetter(AddResourceAction::resource),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AddResourceAction::amount)
    ).apply(instance, AddResourceAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount)) return;
        ResourceHolderData resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        Identifier id = HolderHelper.id(this.resource);
        FormulaContext resourceContext = entity instanceof LivingEntity living
                ? ResourceService.formulaContext(living, id, this.resource.value(), context)
                : context;
        ResourceService.change(resources, id, this.resource.value(), amount, resourceContext);
    }

    @Override
    public MapCodec<AddResourceAction> codec() {
        return CODEC;
    }
}
