package com.iafenvoy.mxt.integration.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Callback storage for the JavaScript condition dispatch types.
 */
public final class MxtJsConditionCallbacks {
    private static final Map<String, BiPredicate<Entity, JsonObject>> ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, TriPredicate<Entity, Entity, JsonObject>> BI_ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, TriPredicate<Level, BlockPos, JsonObject>> BLOCK = new ConcurrentHashMap<>();
    private static final Map<String, TriPredicate<Entity, ItemStack, JsonObject>> ITEM = new ConcurrentHashMap<>();
    private static final Map<String, TriPredicate<DamageSource, Float, JsonObject>> DAMAGE = new ConcurrentHashMap<>();

    private MxtJsConditionCallbacks() {
    }

    public static void registerEntity(String id, BiPredicate<Entity, JsonObject> callback) {
        ENTITY.put(id, callback);
    }

    public static void registerBiEntity(String id, TriPredicate<Entity, Entity, JsonObject> callback) {
        BI_ENTITY.put(id, callback);
    }

    public static void registerBlock(String id, TriPredicate<Level, BlockPos, JsonObject> callback) {
        BLOCK.put(id, callback);
    }

    public static void registerItem(String id, TriPredicate<Entity, ItemStack, JsonObject> callback) {
        ITEM.put(id, callback);
    }

    public static void registerDamage(String id, TriPredicate<DamageSource, Float, JsonObject> callback) {
        DAMAGE.put(id, callback);
    }

    public static boolean testEntity(String id, Entity entity, JsonObject params) {
        BiPredicate<Entity, JsonObject> callback = ENTITY.get(id);
        return callback == null ? unknown("entity condition", id) : test("entity condition", id, () -> callback.test(entity, params));
    }

    public static boolean testBiEntity(String id, Entity actor, Entity target, JsonObject params) {
        TriPredicate<Entity, Entity, JsonObject> callback = BI_ENTITY.get(id);
        return callback == null ? unknown("bi-entity condition", id) : test("bi-entity condition", id, () -> callback.test(actor, target, params));
    }

    public static boolean testBlock(String id, Level level, BlockPos pos, JsonObject params) {
        TriPredicate<Level, BlockPos, JsonObject> callback = BLOCK.get(id);
        return callback == null ? unknown("block condition", id) : test("block condition", id, () -> callback.test(level, pos, params));
    }

    public static boolean testItem(String id, Entity holder, ItemStack stack, JsonObject params) {
        TriPredicate<Entity, ItemStack, JsonObject> callback = ITEM.get(id);
        return callback == null ? unknown("item condition", id) : test("item condition", id, () -> callback.test(holder, stack, params));
    }

    public static boolean testDamage(String id, DamageSource source, float amount, JsonObject params) {
        TriPredicate<DamageSource, Float, JsonObject> callback = DAMAGE.get(id);
        return callback == null ? unknown("damage condition", id) : test("damage condition", id, () -> callback.test(source, amount, params));
    }

    public static void clear() {
        ENTITY.clear();
        BI_ENTITY.clear();
        BLOCK.clear();
        ITEM.clear();
        DAMAGE.clear();
    }

    private static boolean unknown(String type, String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS {} '{}'", type, id);
        return false;
    }

    private static boolean test(String type, String id, BooleanSupplier callback) {
        try {
            return callback.getAsBoolean();
        } catch (Exception exception) {
            MiXianTu.LOGGER.error("KubeJS {} '{}' failed", type, id, exception);
            return false;
        }
    }
}
