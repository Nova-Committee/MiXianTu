package com.iafenvoy.mxt.data.resource;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsValueCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * Script-dispatched implementation kept here because ResourceValueProvider is sealed.
 */
public record JsResourceValueProvider(String id, JsonObject params) implements ResourceValueProvider {
    public static final MapCodec<JsResourceValueProvider> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsResourceValueProvider::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsResourceValueProvider::params)
    ).apply(i, JsResourceValueProvider::new));

    @Override
    public double resolve(ResourceHolderData holder, Holder<Resource> resource, FormulaContext context) {
        return MxtJsValueCallbacks.resource(this.id, holder, resource, context, this.params);
    }

    @Override
    public MapCodec<JsResourceValueProvider> codec() {
        return CODEC;
    }
}
