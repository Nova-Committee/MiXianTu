package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.TriConsumer;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.action.*;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

    @Info("Decodes and executes any registered entity action definition.")
    public void executeEntity(Entity entity, JsonObject definition) {
        MxtKubeJsDataCodec.decode(EntityAction.CODEC, definition, entity.level().registryAccess())
                .execute(entity, FormulaContext.of(entity));
    }

    @Info("Decodes and executes any registered bi-entity action definition.")
    public void executeBiEntity(Entity actor, Entity target, JsonObject definition) {
        MxtKubeJsDataCodec.decode(BiEntityAction.CODEC, definition, actor.level().registryAccess())
                .execute(actor, target, FormulaContext.of(actor));
    }

    @Info("Decodes and executes any registered block action definition.")
    public void executeBlock(Level level, BlockPos pos, JsonObject definition) {
        MxtKubeJsDataCodec.decode(BlockAction.CODEC, definition, level.registryAccess())
                .execute(level, pos, FormulaContext.of(level));
    }

    @Info("Decodes and executes any registered item action definition.")
    public void executeItem(Entity holder, ItemStack stack, JsonObject definition) {
        MxtKubeJsDataCodec.decode(ItemAction.CODEC, definition, holder.level().registryAccess())
                .execute(holder, stack, FormulaContext.of(holder));
    }
}
