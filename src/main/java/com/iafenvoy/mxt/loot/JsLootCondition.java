package com.iafenvoy.mxt.loot;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsLootCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

/**
 * A vanilla loot condition whose decision is a server script callback.
 */
public record JsLootCondition(String id, JsonObject params) implements LootItemCondition {
    public static final MapCodec<JsLootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsLootCondition::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsLootCondition::params)
    ).apply(i, JsLootCondition::new));

    @Override
    public boolean test(@NonNull LootContext context) {
        return MxtJsLootCallbacks.condition(this.id, context, this.params);
    }

    @Override
    public @NonNull MapCodec<JsLootCondition> codec() {
        return CODEC;
    }
}
