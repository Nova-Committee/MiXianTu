package com.iafenvoy.mxt.data.cost.builtin;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A cost whose check and payment are server script callbacks. The implementations live in the optional
 * KubeJS package, but the type is registered unconditionally like every other {@code mxt:js} entry, so a
 * data pack that uses it always loads and only fails to be paid when the script is absent.
 * <p>
 * A script owns whatever state it touches, so one transaction asks it twice: the check before anything is taken and
 * again right before the consume, which is what keeps a single call from skipping the condition. What it takes is the
 * script's own business, so a payment never spells it out and two script calls are never added together.
 * <p>
 * The parameters are frozen when the entry is priced and the frozen copy is never handed to a script: every callback
 * gets a fresh deep copy, so one that writes into what it was given cannot change what the next callback sees - and a
 * simulated pass cannot change the parameters the real one is paid with.
 */
public record JsCost(String id, JsonObject params) implements Cost {
    public static final MapCodec<JsCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(JsCost::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsCost::params)
    ).apply(i, JsCost::new));
    // Two calls of one script are two calls: the entry number is what keeps them apart as targets.
    private static final AtomicLong CALLS = new AtomicLong();

    @Override
    public ResultWithData<Transaction> prepare(CostContext context) {
        // The payer is bound here - a script cannot be paid by somebody else later - and the parameters are frozen as a
        // copy, so nothing a script does to them can reach the definition being replayed. A context without a player is
        // bound as absent and refused when the fee is paid.
        CostTarget target = new CostTarget.Script(this.id(), CALLS.incrementAndGet());
        return ResultWithData.success(new ScriptTransaction(target, context.player(), this.id(), this.params().deepCopy()));
    }

    @Override
    public MapCodec<JsCost> codec() {
        return CODEC;
    }

    // One call has no amount to report, so it is worth "one call": that is the unit the debit names.
    private static final class ScriptTransaction extends CostTransaction {
        private final @Nullable Player player;
        private final String script;
        private final JsonObject params;

        private ScriptTransaction(CostTarget target, @Nullable Player player, String script, JsonObject params) {
            super(target, 1.0D);
            this.player = player;
            this.script = script;
            this.params = params;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.player == null) return refused(CostFailure.NO_CHANNEL);
            if (!MxtJsCostCallbacks.check(this.script, this.player, this.params.deepCopy()))
                return refused(CostFailure.SCRIPT_REJECTED);
            if (simulate) return took(List.of());
            if (!MxtJsCostCallbacks.consume(this.script, this.player, this.params.deepCopy()))
                return refused(CostFailure.SCRIPT_REJECTED);
            return took(Debit.of(this.target(), this.amount()));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new ScriptTransaction(this.target(), this.player, this.script, this.params);
        }
    }
}
