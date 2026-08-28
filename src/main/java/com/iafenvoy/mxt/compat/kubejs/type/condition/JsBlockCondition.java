package com.iafenvoy.mxt.compat.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsBlockCondition(String id, JsonObject params) implements BlockCondition {
    public static final MapCodec<JsBlockCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBlockCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBlockCondition::params)
    ).apply(i, JsBlockCondition::new));

    @Override
    public boolean test(BlockConditionContext context) {
        return MxtJsConditionCallbacks.testBlock(this.id, context.level(), context.pos(), this.params);
    }

    @Override
    public MapCodec<JsBlockCondition> codec() {
        return CODEC;
    }
}
