package com.iafenvoy.mxt.integration.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.callback.TriConsumer;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.BiConsumer;

/**
 * KubeJS registrations for the four intrinsic action dispatch types.
 */
public final class MxtKubeJsActionBindings {
    @Info("Registers an entity action. Datapack type: mxt:js")
    public void entity(String id, BiConsumer<Entity, JsonObject> callback) {
        MxtJsActionCallbacks.registerEntity(id, callback);
    }

    @Info("Registers a bi-entity action. Datapack type: mxt:js")
    public void biEntity(String id, TriConsumer<Entity, Entity, JsonObject> callback) {
        MxtJsActionCallbacks.registerBiEntity(id, callback);
    }

    @Info("Registers a block action. Datapack type: mxt:js")
    public void block(String id, TriConsumer<Level, BlockPos, JsonObject> callback) {
        MxtJsActionCallbacks.registerBlock(id, callback);
    }

    @Info("Registers an item action. Datapack type: mxt:js")
    public void item(String id, TriConsumer<Entity, ItemStack, JsonObject> callback) {
        MxtJsActionCallbacks.registerItem(id, callback);
    }
}
