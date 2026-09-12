package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Callback storage for the JavaScript condition dispatch types.
 *
 * <p>Every callback receives the {@link FormulaContext} the condition was tested with, so a script
 * can read the same event payload variables a data pack condition could. The context is created once
 * by the dispatching system and is not modified by this class.</p>
 */
public final class MxtJsConditionCallbacks {
    private static final Map<String, TriPredicate<Entity, JsonObject, FormulaContext>> ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, QuadPredicate<Entity, Entity, JsonObject, FormulaContext>> BI_ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, QuadPredicate<Level, BlockPos, JsonObject, FormulaContext>> BLOCK = new ConcurrentHashMap<>();
    private static final Map<String, QuadPredicate<Entity, ItemStack, JsonObject, FormulaContext>> ITEM = new ConcurrentHashMap<>();
    private static final Map<String, QuadPredicate<DamageSource, Double, JsonObject, FormulaContext>> DAMAGE = new ConcurrentHashMap<>();

    private MxtJsConditionCallbacks() {
    }

    public static void registerEntity(String id, TriPredicate<Entity, JsonObject, FormulaContext> callback) {
        ENTITY.put(id, callback);
    }

    public static void registerBiEntity(String id, QuadPredicate<Entity, Entity, JsonObject, FormulaContext> callback) {
        BI_ENTITY.put(id, callback);
    }

    public static void registerBlock(String id, QuadPredicate<Level, BlockPos, JsonObject, FormulaContext> callback) {
        BLOCK.put(id, callback);
    }

    public static void registerItem(String id, QuadPredicate<Entity, ItemStack, JsonObject, FormulaContext> callback) {
        ITEM.put(id, callback);
    }

    public static void registerDamage(String id, QuadPredicate<DamageSource, Double, JsonObject, FormulaContext> callback) {
        DAMAGE.put(id, callback);
    }

    public static boolean testEntity(String id, Entity entity, JsonObject params, FormulaContext context) {
        TriPredicate<Entity, JsonObject, FormulaContext> callback = ENTITY.get(id);
        return callback == null ? unknown("entity condition", id)
                : test("entity condition", id, () -> callback.test(entity, params, context));
    }

    public static boolean testBiEntity(String id, Entity actor, Entity target, JsonObject params, FormulaContext context) {
        QuadPredicate<Entity, Entity, JsonObject, FormulaContext> callback = BI_ENTITY.get(id);
        return callback == null ? unknown("bi-entity condition", id)
                : test("bi-entity condition", id, () -> callback.test(actor, target, params, context));
    }

    public static boolean testBlock(String id, Level level, BlockPos pos, JsonObject params, FormulaContext context) {
        QuadPredicate<Level, BlockPos, JsonObject, FormulaContext> callback = BLOCK.get(id);
        return callback == null ? unknown("block condition", id)
                : test("block condition", id, () -> callback.test(level, pos, params, context));
    }

    public static boolean testItem(String id, Entity holder, ItemStack stack, JsonObject params, FormulaContext context) {
        QuadPredicate<Entity, ItemStack, JsonObject, FormulaContext> callback = ITEM.get(id);
        return callback == null ? unknown("item condition", id)
                : test("item condition", id, () -> callback.test(holder, stack, params, context));
    }

    public static boolean testDamage(String id, DamageSource source, double amount, JsonObject params, FormulaContext context) {
        QuadPredicate<DamageSource, Double, JsonObject, FormulaContext> callback = DAMAGE.get(id);
        return callback == null ? unknown("damage condition", id)
                : test("damage condition", id, () -> callback.test(source, amount, params, context));
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
