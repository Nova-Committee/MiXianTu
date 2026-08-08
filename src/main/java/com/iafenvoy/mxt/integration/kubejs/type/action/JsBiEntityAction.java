package com.iafenvoy.mxt.integration.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

public record JsBiEntityAction(String id, JsonObject params) implements BiEntityAction {
    public static final MapCodec<JsBiEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsBiEntityAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsBiEntityAction::params)
    ).apply(instance, JsBiEntityAction::new));

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        MxtJsActionCallbacks.executeBiEntity(this.id, actor, target, this.params);
    }

    @Override
    public MapCodec<JsBiEntityAction> codec() {
        return CODEC;
    }
}
