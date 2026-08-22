package com.iafenvoy.mxt.compat.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

public record JsEntityAction(String id, JsonObject params) implements EntityAction {
    public static final MapCodec<JsEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsEntityAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsEntityAction::params)
    ).apply(i, JsEntityAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        MxtJsActionCallbacks.executeEntity(this.id, entity, this.params);
    }

    @Override
    public MapCodec<JsEntityAction> codec() {
        return CODEC;
    }
}
