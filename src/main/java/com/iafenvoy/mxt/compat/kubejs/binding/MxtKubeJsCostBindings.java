package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.cost.Cost;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.player.Player;

/**
 * Complete data-driven cost operations exposed as {@code MxtCosts}.
 */
public final class MxtKubeJsCostBindings {
    @Info("Checks one complete MXT cost definition without changing player state.")
    public boolean check(Player player, JsonElement definition) {
        return MxtKubeJsDataCodec.decode(Cost.CODEC, definition, player.level().registryAccess()).check(player);
    }

    @Info("Checks and consumes one complete MXT cost definition. Returns false without consuming when it cannot be paid.")
    public boolean consume(Player player, JsonElement definition) {
        Cost cost = MxtKubeJsDataCodec.decode(Cost.CODEC, definition, player.level().registryAccess());
        if (!cost.check(player)) return false;
        cost.consume(player);
        return true;
    }
}
