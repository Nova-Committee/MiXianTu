package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsLootCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.TriFunction;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.function.BiPredicate;

/**
 * Script-backed vanilla loot conditions and functions exposed as {@code MxtLoot}.
 *
 * <p>Both run on the server while loot is generated, so a loot table can call into a server script
 * exactly like it calls a built-in condition or function.</p>
 */
public final class MxtKubeJsLootBindings {
    @Info("Registers a script loot condition. Vanilla condition type: mxt:js")
    public void condition(String id, BiPredicate<LootContext, JsonObject> callback) {
        MxtJsLootCallbacks.registerCondition(id, callback);
    }

    @Info("Registers a script loot function. Return the stack to keep or a new stack; null keeps the original. Vanilla function type: mxt:js")
    public void function(String id, TriFunction<ItemStack, LootContext, JsonObject, ItemStack> callback) {
        MxtJsLootCallbacks.registerFunction(id, callback);
    }
}
