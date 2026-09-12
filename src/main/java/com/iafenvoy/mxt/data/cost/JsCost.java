package com.iafenvoy.mxt.data.cost;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

/**
 * A cost whose check and payment are server script callbacks.
 *
 * <p>The callback implementations live in the optional KubeJS package, but this type is registered
 * unconditionally like every other {@code mxt:js} entry, so a data pack that uses it always loads
 * and only fails to be paid when the script that defines the id is absent.</p>
 */
public record JsCost(String id, JsonObject params) implements Cost {
    public static final MapCodec<JsCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsCost::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsCost::params)
    ).apply(i, JsCost::new));

    @Override
    public boolean check(Player player) {
        return MxtJsCostCallbacks.check(this.id, player, this.params);
    }

    @Override
    public void consume(Player player) {
        MxtJsCostCallbacks.consume(this.id, player, this.params);
    }

    @Override
    public MapCodec<JsCost> codec() {
        return CODEC;
    }
}
