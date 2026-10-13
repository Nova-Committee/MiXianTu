package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Resolves a resource-derived value at runtime without exposing attachment mutation to data.
 */
public interface ResourceValueProvider {
    Codec<ResourceValueProvider> CODEC = MxtRegistries.RESOURCE_VALUE_PROVIDER_TYPE.byNameCodec().dispatch("type", ResourceValueProvider::codec, Function.identity());

    double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context);

    // Providers that depend on world state override this overload; attachment-backed ones keep the original.
    default double resolve(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        return this.resolve(entity.getData(MxtAttachments.RESOURCE_HOLDER), resource, context);
    }

    MapCodec<? extends ResourceValueProvider> codec();
}
