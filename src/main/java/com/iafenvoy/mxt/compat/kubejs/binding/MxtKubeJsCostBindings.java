package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.MxtJsWarnings;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCostCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.TriConsumer;
import com.iafenvoy.mxt.compat.kubejs.callback.TriPredicate;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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
        return CostTransaction.plan(List.of(cost), CostContext.of(player, CostOrigin.SCRIPT)).ok();
    }

    @Info("Checks and consumes one complete MXT cost definition. Returns false without consuming when it cannot be paid.")
    public boolean consume(Player player, JsonElement definition) {
        if (player.level().isClientSide()) {
            MxtJsWarnings.warnOnce("cost.client", "MxtCosts.consume was called from a client script; nothing was consumed");
            return false;
        }
        Cost cost = MxtKubeJsDataCodec.decodeCached(Cost.CODEC, definition, player.level().registryAccess());
        return CostTransaction.pay(List.of(cost), CostContext.of(player, CostOrigin.SCRIPT)).paid();
    }
}
