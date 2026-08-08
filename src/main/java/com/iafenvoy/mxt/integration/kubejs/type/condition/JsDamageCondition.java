package com.iafenvoy.mxt.integration.kubejs.type.condition;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;

public record JsDamageCondition(String id, JsonObject params) implements DamageCondition {
    public static final MapCodec<JsDamageCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(JsDamageCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsDamageCondition::params)
    ).apply(instance, JsDamageCondition::new));

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        return MxtJsConditionCallbacks.testDamage(this.id, source, amount, this.params);
    }

    @Override
    public MapCodec<JsDamageCondition> codec() {
        return CODEC;
    }
}
