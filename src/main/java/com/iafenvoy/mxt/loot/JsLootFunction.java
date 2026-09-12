package com.iafenvoy.mxt.loot;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsLootCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A vanilla loot function whose behaviour is a server script callback, with the usual vanilla
 * {@code conditions} field available through {@link LootItemConditionalFunction}.
 */
public final class JsLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<JsLootFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            Codec.STRING.fieldOf("id").forGetter(function -> function.id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(function -> function.params)
    )).apply(i, JsLootFunction::new));

    private final String id;
    private final JsonObject params;

    private JsLootFunction(List<LootItemCondition> conditions, String id, JsonObject params) {
        super(conditions);
        this.id = id;
        this.params = params;
    }

    @Override
    public @NonNull MapCodec<JsLootFunction> codec() {
        return CODEC;
    }

    @Override
    public @NonNull ItemStack run(@NonNull ItemStack stack, @NonNull LootContext context) {
        return MxtJsLootCallbacks.function(this.id, stack, context, this.params);
    }
}
