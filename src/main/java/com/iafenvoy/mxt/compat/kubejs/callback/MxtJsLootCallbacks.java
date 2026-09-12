package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Callback storage for the JavaScript loot condition and loot function types.
 *
 * <p>Both run on the server while loot is generated. A loot function callback may return a
 * replacement stack; returning {@code null}, a missing callback or a failure leaves the generated
 * stack untouched.</p>
 */
public final class MxtJsLootCallbacks {
    private static final Map<String, BiPredicate<LootContext, JsonObject>> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String, TriFunction<ItemStack, LootContext, JsonObject, ItemStack>> FUNCTIONS = new ConcurrentHashMap<>();

    private MxtJsLootCallbacks() {
    }

    public static void registerCondition(String id, BiPredicate<LootContext, JsonObject> callback) {
        CONDITIONS.put(id, callback);
    }

    public static void registerFunction(String id, TriFunction<ItemStack, LootContext, JsonObject, ItemStack> callback) {
        FUNCTIONS.put(id, callback);
    }

    public static boolean condition(String id, LootContext context, JsonObject params) {
        BiPredicate<LootContext, JsonObject> callback = CONDITIONS.get(id);
        if (callback == null) return unknownCondition(id);
        try {
            return callback.test(context, params);
        } catch (Exception exception) {
            return failedCondition(id, exception);
        }
    }

    public static ItemStack function(String id, ItemStack stack, LootContext context, JsonObject params) {
        TriFunction<ItemStack, LootContext, JsonObject, ItemStack> callback = FUNCTIONS.get(id);
        if (callback == null) {
            unknownFunction(id);
            return stack;
        }
        try {
            ItemStack result = callback.apply(stack, context, params);
            return result == null ? stack : result;
        } catch (Exception exception) {
            failedFunction(id, exception);
            return stack;
        }
    }

    public static void clear() {
        CONDITIONS.clear();
        FUNCTIONS.clear();
    }

    private static boolean unknownCondition(String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS loot condition '{}'; the condition is false", id);
        return false;
    }

    private static boolean failedCondition(String id, Exception exception) {
        MiXianTu.LOGGER.error("KubeJS loot condition '{}' failed; the condition is false", id, exception);
        return false;
    }

    private static void unknownFunction(String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS loot function '{}'; the stack is left unchanged", id);
    }

    private static void failedFunction(String id, Exception exception) {
        MiXianTu.LOGGER.error("KubeJS loot function '{}' failed; the stack is left unchanged", id, exception);
    }
}
