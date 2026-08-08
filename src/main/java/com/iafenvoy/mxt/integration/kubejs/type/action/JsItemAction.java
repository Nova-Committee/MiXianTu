package com.iafenvoy.mxt.integration.kubejs.type.action;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record JsItemAction(String id, JsonObject params) implements ItemAction {
    public static final MapCodec<JsItemAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsItemAction::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsItemAction::params)
    ).apply(instance, JsItemAction::new));

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        MxtJsActionCallbacks.executeItem(this.id, holder, stack, this.params);
    }

    @Override
    public MapCodec<JsItemAction> codec() {
        return CODEC;
    }
}
