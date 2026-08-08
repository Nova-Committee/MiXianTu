package com.iafenvoy.mxt.integration.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

public record JsEntityAction(String id, JsonObject params) implements EntityAction {
    public static final MapCodec<JsEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsEntityAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsEntityAction::params)
    ).apply(instance, JsEntityAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        MxtJsActionCallbacks.executeEntity(this.id, entity, this.params);
    }

    @Override
    public MapCodec<JsEntityAction> codec() {
        return CODEC;
    }
}
