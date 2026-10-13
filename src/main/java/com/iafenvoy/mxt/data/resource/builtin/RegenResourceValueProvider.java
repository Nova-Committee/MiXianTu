package com.iafenvoy.mxt.data.resource.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

public enum RegenResourceValueProvider implements ResourceValueProvider {
    INSTANCE;

    public static final MapCodec<RegenResourceValueProvider> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return AuraLookup.find(AuraLookup.access(context), resource)
                .map(profile -> profile.regen().evaluate(context)).orElse(0.0D);
    }

    @Override
    public MapCodec<RegenResourceValueProvider> codec() {
        return CODEC;
    }
}
