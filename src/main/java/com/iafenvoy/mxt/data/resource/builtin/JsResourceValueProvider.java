package com.iafenvoy.mxt.data.resource.builtin;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsValueCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

public record JsResourceValueProvider(String id, JsonObject params) implements ResourceValueProvider {
    public static final MapCodec<JsResourceValueProvider> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsResourceValueProvider::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsResourceValueProvider::params)
    ).apply(i, JsResourceValueProvider::new));

    @Override
    public double resolve(ResourceHolderAttachment holder, Holder<Resource> resource, FormulaContext context) {
        return MxtJsValueCallbacks.resource(this.id, holder, resource, context, this.params);
    }

    @Override
    public MapCodec<JsResourceValueProvider> codec() {
        return CODEC;
    }
}
