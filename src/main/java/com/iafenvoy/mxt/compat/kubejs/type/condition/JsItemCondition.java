package com.iafenvoy.mxt.compat.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record JsItemCondition(String id, JsonObject params) implements ItemCondition {
    public static final MapCodec<JsItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsItemCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsItemCondition::params)
    ).apply(i, JsItemCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext context) {
        return MxtJsConditionCallbacks.testItem(this.id, context.holder(), context.stack(), this.params);
    }

    @Override
    public @NonNull MapCodec<JsItemCondition> codec() {
        return CODEC;
    }
}
