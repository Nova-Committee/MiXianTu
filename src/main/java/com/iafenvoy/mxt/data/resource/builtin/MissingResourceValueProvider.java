package com.iafenvoy.mxt.data.resource.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

public enum MissingResourceValueProvider implements ResourceValueProvider {
    INSTANCE;

    public static final MapCodec<MissingResourceValueProvider> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return Math.max(0.0D, resource.value().max().evaluate(context) - holder.get(resource));
    }

    @Override
    public MapCodec<MissingResourceValueProvider> codec() {
        return CODEC;
    }
}
