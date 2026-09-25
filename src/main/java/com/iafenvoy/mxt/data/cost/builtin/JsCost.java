package com.iafenvoy.mxt.data.cost.builtin;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.cost.Charge;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

/**
 * A cost whose check and payment are server script callbacks. The implementations live in the optional
 * KubeJS package, but the type is registered unconditionally like every other {@code mxt:js} entry, so a
 * data pack that uses it always loads and only fails to be paid when the script is absent.
 * <p>
 * This is the one entry that cannot be staged: the script owns whatever state it touches. It is therefore checked
 * while planning and paid last, after every other channel has been written, and the script is expected to be
 * idempotent about it.
 */
public record JsCost(String id, JsonObject params) implements Cost {
    public static final MapCodec<JsCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsCost::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsCost::params)
    ).apply(i, JsCost::new));

    /**
     * True when the script accepted the payment. Only ever false for an absent or failing callback.
     */
    public boolean consume(CostContext context) {
        Player player = context.player();
        return player != null && MxtJsCostCallbacks.consume(this.id, player, this.params);
    }

    @Override
    public Either<Charge, CostFailure> charge(CostContext context) {
        Player player = context.player();
        if (player == null) return Either.right(CostFailure.NO_CHANNEL);
        return MxtJsCostCallbacks.check(this.id, player, this.params)
                ? Either.left(new Charge.Script(this)) : Either.right(CostFailure.SCRIPT_REJECTED);
    }

    @Override
    public MapCodec<JsCost> codec() {
        return CODEC;
    }
}
