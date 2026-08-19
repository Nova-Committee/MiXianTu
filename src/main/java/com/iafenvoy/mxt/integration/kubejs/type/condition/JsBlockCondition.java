package com.iafenvoy.mxt.integration.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record JsBlockCondition(String id, JsonObject params) implements BlockCondition {
    public static final MapCodec<JsBlockCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBlockCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBlockCondition::params)
    ).apply(i, JsBlockCondition::new));

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        return MxtJsConditionCallbacks.testBlock(this.id, level, pos, this.params);
    }

    @Override
    public MapCodec<JsBlockCondition> codec() {
        return CODEC;
    }
}
