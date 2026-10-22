package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.MxtJsWarnings;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.function.TriConsumer;
import com.iafenvoy.mxt.util.function.TriPredicate;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Complete data-driven cost operations exposed as {@code MxtCosts}.
 */
public final class MxtKubeJsCostBindings {
    @Info("Registers a script cost. Datapack type: mxt:js")
    public void register(String id, TriPredicate<Player, JsonObject, FormulaContext> check,
                         TriConsumer<Player, JsonObject, FormulaContext> consume) {
        MxtJsCostCallbacks.register(id, check, consume);
    }

    @Info("Checks one complete MXT cost definition without changing player state.")
    public boolean check(Player player, JsonElement definition) {
        Cost cost = MxtKubeJsDataCodec.decodeCached(Cost.CODEC, definition, player.level().registryAccess());
        CostPayment payment = CostPayment.of(CostContext.of(player, CostOrigins.SCRIPT));
        return payment.price(List.of(cost)).isEmpty() && payment.test().isEmpty();
    }

    @Info("Simulates one MXT cost, then attempts its deduction; a real failure only warns and still returns true.")
    public boolean consume(Player player, JsonElement definition) {
        if (player.level().isClientSide()) {
            MxtJsWarnings.warnOnce("cost.client", "MxtCosts.consume was called from a client script; nothing was consumed");
            return false;
        }
        Cost cost = MxtKubeJsDataCodec.decodeCached(Cost.CODEC, definition, player.level().registryAccess());
        // One definition is one fee, and it pays through the same bill every other caller uses - so a script cannot
        // reach a channel the common payment would have checked.
        return CostPayment.pay(List.of(cost), CostContext.of(player, CostOrigins.SCRIPT)).isSuccess();
    }
}
