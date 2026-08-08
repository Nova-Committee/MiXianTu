package com.iafenvoy.mxt.integration.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

public record JsBiEntityCondition(String id, JsonObject params) implements BiEntityCondition {
    public static final MapCodec<JsBiEntityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsBiEntityCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBiEntityCondition::params)
    ).apply(instance, JsBiEntityCondition::new));

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return MxtJsConditionCallbacks.testBiEntity(this.id, actor, target, this.params);
    }

    @Override
    public MapCodec<JsBiEntityCondition> codec() {
        return CODEC;
    }
}
