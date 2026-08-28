package com.iafenvoy.mxt.compat.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsEntityCondition(String id, JsonObject params) implements EntityCondition {
    public static final MapCodec<JsEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsEntityCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsEntityCondition::params)
    ).apply(i, JsEntityCondition::new));

    @Override
    public boolean test(EntityConditionContext context) {
        return MxtJsConditionCallbacks.testEntity(this.id, context.entity(), this.params);
    }

    @Override
    public MapCodec<JsEntityCondition> codec() {
        return CODEC;
    }
}
