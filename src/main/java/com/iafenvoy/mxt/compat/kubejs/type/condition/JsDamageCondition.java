package com.iafenvoy.mxt.compat.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record JsDamageCondition(String id, JsonObject params) implements DamageCondition {
    public static final MapCodec<JsDamageCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsDamageCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsDamageCondition::params)
    ).apply(i, JsDamageCondition::new));

    @Override
    public boolean test(@NonNull DamageConditionContext context) {
        return MxtJsConditionCallbacks.testDamage(this.id, context.source(), context.amount(), this.params);
    }

    @Override
    public @NonNull MapCodec<JsDamageCondition> codec() {
        return CODEC;
    }
}
