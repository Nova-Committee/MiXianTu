package com.iafenvoy.mxt.integration.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

public record JsEntityCondition(String id, JsonObject params) implements EntityCondition {
    public static final MapCodec<JsEntityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsEntityCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsEntityCondition::params)
    ).apply(instance, JsEntityCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return MxtJsConditionCallbacks.testEntity(this.id, entity, this.params);
    }

    @Override
    public MapCodec<JsEntityCondition> codec() {
        return CODEC;
    }
}
