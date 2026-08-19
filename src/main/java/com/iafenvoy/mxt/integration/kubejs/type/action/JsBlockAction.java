package com.iafenvoy.mxt.integration.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record JsBlockAction(String id, JsonObject params) implements BlockAction {
    public static final MapCodec<JsBlockAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBlockAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBlockAction::params)
    ).apply(i, JsBlockAction::new));

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        MxtJsActionCallbacks.executeBlock(this.id, level, pos, this.params);
    }

    @Override
    public MapCodec<JsBlockAction> codec() {
        return CODEC;
    }
}
