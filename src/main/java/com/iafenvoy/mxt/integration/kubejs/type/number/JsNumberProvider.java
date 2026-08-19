package com.iafenvoy.mxt.integration.kubejs.type.number;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsValueCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsNumberProvider(String id, JsonObject params) implements NumberProvider {
    public static final MapCodec<JsNumberProvider> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsNumberProvider::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsNumberProvider::params)
    ).apply(i, JsNumberProvider::new));

    @Override
    public double evaluate(FormulaContext context) {
        return MxtJsValueCallbacks.number(this.id, context, this.params);
    }

    @Override
    public MapCodec<JsNumberProvider> codec() {
        return MAP_CODEC;
    }
}
