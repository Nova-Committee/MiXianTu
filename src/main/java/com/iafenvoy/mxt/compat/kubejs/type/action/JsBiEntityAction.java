package com.iafenvoy.mxt.compat.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record JsBiEntityAction(String id, JsonObject params) implements BiEntityAction {
    public static final MapCodec<JsBiEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsBiEntityAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBiEntityAction::params)
    ).apply(i, JsBiEntityAction::new));

    @Override
    public void execute(@NonNull BiEntityActionContext context) {
        MxtJsActionCallbacks.executeBiEntity(this.id, context.actor(), context.target(), this.params);
    }

    @Override
    public @NonNull MapCodec<JsBiEntityAction> codec() {
        return CODEC;
    }
}
