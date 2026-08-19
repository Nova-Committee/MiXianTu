package com.iafenvoy.mxt.integration.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record JsItemCondition(String id, JsonObject params) implements ItemCondition {
    public static final MapCodec<JsItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsItemCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsItemCondition::params)
    ).apply(i, JsItemCondition::new));

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return MxtJsConditionCallbacks.testItem(this.id, holder, stack, this.params);
    }

    @Override
    public MapCodec<JsItemCondition> codec() {
        return CODEC;
    }
}
