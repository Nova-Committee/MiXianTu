package com.iafenvoy.mxt.integration.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Callback storage for the JavaScript action dispatch types.
 */
public final class MxtJsActionCallbacks {
    private static final Map<String, BiConsumer<Entity, JsonObject>> ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, TriConsumer<Entity, Entity, JsonObject>> BI_ENTITY = new ConcurrentHashMap<>();
    private static final Map<String, TriConsumer<Level, BlockPos, JsonObject>> BLOCK = new ConcurrentHashMap<>();
    private static final Map<String, TriConsumer<Entity, ItemStack, JsonObject>> ITEM = new ConcurrentHashMap<>();

    private MxtJsActionCallbacks() {
    }

    public static void registerEntity(String id, BiConsumer<Entity, JsonObject> callback) {
        ENTITY.put(id, callback);
    }

    public static void registerBiEntity(String id, TriConsumer<Entity, Entity, JsonObject> callback) {
        BI_ENTITY.put(id, callback);
    }

    public static void registerBlock(String id, TriConsumer<Level, BlockPos, JsonObject> callback) {
        BLOCK.put(id, callback);
    }

    public static void registerItem(String id, TriConsumer<Entity, ItemStack, JsonObject> callback) {
        ITEM.put(id, callback);
    }

    public static void executeEntity(String id, Entity entity, JsonObject params) {
        BiConsumer<Entity, JsonObject> callback = ENTITY.get(id);
        if (callback == null) {
            unknown("entity action", id);
            return;
        }
        run("entity action", id, () -> callback.accept(entity, params));
    }

    public static void executeBiEntity(String id, Entity actor, Entity target, JsonObject params) {
        TriConsumer<Entity, Entity, JsonObject> callback = BI_ENTITY.get(id);
        if (callback == null) {
            unknown("bi-entity action", id);
            return;
        }
        run("bi-entity action", id, () -> callback.accept(actor, target, params));
    }

    public static void executeBlock(String id, Level level, BlockPos pos, JsonObject params) {
        TriConsumer<Level, BlockPos, JsonObject> callback = BLOCK.get(id);
        if (callback == null) {
            unknown("block action", id);
            return;
        }
        run("block action", id, () -> callback.accept(level, pos, params));
    }

    public static void executeItem(String id, Entity holder, ItemStack stack, JsonObject params) {
        TriConsumer<Entity, ItemStack, JsonObject> callback = ITEM.get(id);
        if (callback == null) {
            unknown("item action", id);
            return;
        }
        run("item action", id, () -> callback.accept(holder, stack, params));
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
