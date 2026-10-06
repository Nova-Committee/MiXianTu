package com.iafenvoy.mxt.data.cost.builtin;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * A cost whose check and payment are server script callbacks. The implementations live in the optional
 * KubeJS package, but the type is registered unconditionally like every other {@code mxt:js} entry, so a
 * data pack that uses it always loads and only fails to be paid when the script is absent.
 * <p>
 * This is the one entry with no draft: the script owns whatever state it touches, so it cannot be merged with
 * another entry and cannot be put back. It is therefore asked when it is loaded - which is where an array that
 * cannot be paid stops - and paid last, after every channel that can be put back, and the script is expected to be
 * idempotent about it.
 */
public record JsCost(String id, JsonObject params) implements Cost {
    public static final MapCodec<JsCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsCost::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsCost::params)
    ).apply(i, JsCost::new));

    /**
     * The script's own read-only answer. Only ever a failure for an absent or failing callback.
     */
    @Override
    public Optional<CostFailure> test(CostContext context) {
        Player player = context.player();
        if (player == null) return Optional.of(CostFailure.NO_CHANNEL);
        return MxtJsCostCallbacks.check(this.id, player, this.params)
                ? Optional.empty() : Optional.of(CostFailure.SCRIPT_REJECTED);
    }

    @Override
    public Optional<CostFailure> commit(CostContext context) {
        Player player = context.player();
        if (player == null) return Optional.of(CostFailure.NO_CHANNEL);
        return MxtJsCostCallbacks.consume(this.id, player, this.params)
                ? Optional.empty() : Optional.of(CostFailure.SCRIPT_REJECTED);
    }

    @Override
    public MapCodec<JsCost> codec() {
        return CODEC;
    }
}
