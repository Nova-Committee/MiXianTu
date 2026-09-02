package com.iafenvoy.mxt.compat.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record JsBlockAction(String id, JsonObject params) implements BlockAction {
    public static final MapCodec<JsBlockAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBlockAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBlockAction::params)
    ).apply(i, JsBlockAction::new));

    @Override
    public void execute(@NonNull BlockActionContext context) {
        MxtJsActionCallbacks.executeBlock(this.id, context.level(), context.pos(), this.params);
    }

    @Override
    public @NonNull MapCodec<JsBlockAction> codec() {
        return CODEC;
    }
}
