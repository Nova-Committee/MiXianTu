package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsConditionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.TriPredicate;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.condition.*;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

    @Info("Decodes and evaluates any registered entity condition definition.")
    public boolean testEntity(Entity entity, JsonObject definition) {
        return MxtKubeJsDataCodec.decode(EntityCondition.CODEC, definition, entity.level().registryAccess())
                .test(entity, FormulaContext.of(entity));
    }

    @Info("Decodes and evaluates any registered bi-entity condition definition.")
    public boolean testBiEntity(Entity actor, Entity target, JsonObject definition) {
        return MxtKubeJsDataCodec.decode(BiEntityCondition.CODEC, definition, actor.level().registryAccess())
                .test(actor, target, FormulaContext.of(actor));
    }

    @Info("Decodes and evaluates any registered block condition definition.")
    public boolean testBlock(Level level, BlockPos pos, JsonObject definition) {
        return MxtKubeJsDataCodec.decode(BlockCondition.CODEC, definition, level.registryAccess())
                .test(level, pos, FormulaContext.of(level));
    }

    @Info("Decodes and evaluates any registered item condition definition.")
    public boolean testItem(Entity holder, ItemStack stack, JsonObject definition) {
        return MxtKubeJsDataCodec.decode(ItemCondition.CODEC, definition, holder.level().registryAccess())
                .test(holder, stack, FormulaContext.of(holder));
    }

    @Info("Decodes and evaluates any registered damage condition definition.")
    public boolean testDamage(Level level, DamageSource source, float amount, JsonObject definition) {
        FormulaContext context = source.getEntity() == null ? FormulaContext.EMPTY : FormulaContext.of(source.getEntity());
        return MxtKubeJsDataCodec.decode(DamageCondition.CODEC, definition, level.registryAccess())
                .test(source, amount, context);
    }
}
