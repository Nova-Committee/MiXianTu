package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Callback storage for the JavaScript action dispatch types.
 *
 * <p>Every callback receives the {@link FormulaContext} the action was dispatched with, so a script
 * can read the same event payload variables a data pack action could. The context is created once by
 * the dispatching system and is not modified by this class.</p>
 */
public final class MxtJsActionCallbacks {
    private static final Map<String, TriConsumer<Entity, JsonObject, FormulaContext>> ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, QuadConsumer<Entity, Entity, JsonObject, FormulaContext>> BI_ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, QuadConsumer<Level, BlockPos, JsonObject, FormulaContext>> BLOCK = new ConcurrentHashMap<>();
    private static final Map<String, QuadConsumer<Entity, ItemStack, JsonObject, FormulaContext>> ITEM = new ConcurrentHashMap<>();

    private MxtJsActionCallbacks() {
    }

    public static void registerEntity(String id, TriConsumer<Entity, JsonObject, FormulaContext> callback) {
        ENTITY.put(id, callback);
    }

    public static void registerBiEntity(String id, QuadConsumer<Entity, Entity, JsonObject, FormulaContext> callback) {
        BI_ENTITY.put(id, callback);
    }

    public static void registerBlock(String id, QuadConsumer<Level, BlockPos, JsonObject, FormulaContext> callback) {
        BLOCK.put(id, callback);
    }

    public static void registerItem(String id, QuadConsumer<Entity, ItemStack, JsonObject, FormulaContext> callback) {
        ITEM.put(id, callback);
    }

    public static void executeEntity(String id, Entity entity, JsonObject params, FormulaContext context) {
        TriConsumer<Entity, JsonObject, FormulaContext> callback = ENTITY.get(id);
        if (callback == null) {
            unknown("entity action", id);
            return;
        }
        run("entity action", id, () -> callback.accept(entity, params, context));
    }

    public static void executeBiEntity(String id, Entity actor, Entity target, JsonObject params, FormulaContext context) {
        QuadConsumer<Entity, Entity, JsonObject, FormulaContext> callback = BI_ENTITY.get(id);
        if (callback == null) {
            unknown("bi-entity action", id);
            return;
        }
        run("bi-entity action", id, () -> callback.accept(actor, target, params, context));
    }

    public static void executeBlock(String id, Level level, BlockPos pos, JsonObject params, FormulaContext context) {
        QuadConsumer<Level, BlockPos, JsonObject, FormulaContext> callback = BLOCK.get(id);
        if (callback == null) {
            unknown("block action", id);
            return;
        }
        run("block action", id, () -> callback.accept(level, pos, params, context));
    }

    public static void executeItem(String id, Entity holder, ItemStack stack, JsonObject params, FormulaContext context) {
        QuadConsumer<Entity, ItemStack, JsonObject, FormulaContext> callback = ITEM.get(id);
        if (callback == null) {
            unknown("item action", id);
            return;
        }
        run("item action", id, () -> callback.accept(holder, stack, params, context));
    }

    public static void clear() {
        ENTITY.clear();
        BI_ENTITY.clear();
        BLOCK.clear();
        ITEM.clear();
    }

    private static void unknown(String type, String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS {} '{}'", type, id);
    }

    private static void run(String type, String id, Runnable callback) {
        try {
            callback.run();
        } catch (Exception exception) {
            MiXianTu.LOGGER.error("KubeJS {} '{}' failed", type, id, exception);
        }
    }
}
