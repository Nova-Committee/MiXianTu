package com.iafenvoy.mxt.compat.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record JsBiEntityCondition(String id, JsonObject params) implements BiEntityCondition {
    public static final MapCodec<JsBiEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBiEntityCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBiEntityCondition::params)
    ).apply(i, JsBiEntityCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext context) {
        return MxtJsConditionCallbacks.testBiEntity(this.id, context.actor(), context.target(), this.params);
    }

    @Override
    public @NonNull MapCodec<JsBiEntityCondition> codec() {
        return CODEC;
    }
}
