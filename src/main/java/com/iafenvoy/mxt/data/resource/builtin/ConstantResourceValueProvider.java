package com.iafenvoy.mxt.data.resource.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

public record ConstantResourceValueProvider(NumberProvider value) implements ResourceValueProvider {
    public static final MapCodec<ConstantResourceValueProvider> CODEC = NumberProvider.CODEC.fieldOf("value")
            .xmap(ConstantResourceValueProvider::new, ConstantResourceValueProvider::value);

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return this.value.evaluate(context);
    }

    @Override
    public MapCodec<ConstantResourceValueProvider> codec() {
        return CODEC;
    }
}
