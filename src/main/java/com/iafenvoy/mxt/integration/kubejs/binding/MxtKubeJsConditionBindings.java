package com.iafenvoy.mxt.integration.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.integration.kubejs.callback.TriPredicate;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.BiPredicate;

/**
 * KubeJS registrations for the five intrinsic condition dispatch types.
 */
public final class MxtKubeJsConditionBindings {
    @Info("Registers an entity condition. Datapack type: mxt:js")
    public void entity(String id, BiPredicate<Entity, JsonObject> callback) {
        MxtJsConditionCallbacks.registerEntity(id, callback);
    }

    @Info("Registers a bi-entity condition. Datapack type: mxt:js")
    public void biEntity(String id, TriPredicate<Entity, Entity, JsonObject> callback) {
        MxtJsConditionCallbacks.registerBiEntity(id, callback);
    }

    @Info("Registers a block condition. Datapack type: mxt:js")
    public void block(String id, TriPredicate<Level, BlockPos, JsonObject> callback) {
        MxtJsConditionCallbacks.registerBlock(id, callback);
    }

    @Info("Registers an item condition. Datapack type: mxt:js")
    public void item(String id, TriPredicate<Entity, ItemStack, JsonObject> callback) {
        MxtJsConditionCallbacks.registerItem(id, callback);
    }

    @Info("Registers a damage condition. Datapack type: mxt:js")
    public void damage(String id, TriPredicate<DamageSource, Float, JsonObject> callback) {
        MxtJsConditionCallbacks.registerDamage(id, callback);
    }
}
