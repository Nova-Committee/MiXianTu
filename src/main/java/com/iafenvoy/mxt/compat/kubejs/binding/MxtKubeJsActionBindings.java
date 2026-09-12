package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsActionCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.QuadConsumer;
import com.iafenvoy.mxt.compat.kubejs.callback.TriConsumer;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * KubeJS registrations for the four intrinsic action dispatch types.
 *
 * <p>Callbacks receive the {@link FormulaContext} of the dispatch as their last argument, so the
 * same event payload variables a data pack action reads are available to the script.</p>
 */
public final class MxtKubeJsActionBindings {
    @Info("Registers an entity action. Datapack type: mxt:js")
    public void entity(String id, TriConsumer<Entity, JsonObject, FormulaContext> callback) {
        MxtJsActionCallbacks.registerEntity(id, callback);
    }

    @Info("Registers a bi-entity action. Datapack type: mxt:js")
    public void biEntity(String id, QuadConsumer<Entity, Entity, JsonObject, FormulaContext> callback) {
        MxtJsActionCallbacks.registerBiEntity(id, callback);
    }

    @Info("Registers a block action. Datapack type: mxt:js")
    public void block(String id, QuadConsumer<Level, BlockPos, JsonObject, FormulaContext> callback) {
        MxtJsActionCallbacks.registerBlock(id, callback);
    }

    @Info("Registers an item action. Datapack type: mxt:js")
    public void item(String id, QuadConsumer<Entity, ItemStack, JsonObject, FormulaContext> callback) {
        MxtJsActionCallbacks.registerItem(id, callback);
    }

    @Info("Decodes and executes any registered entity action definition.")
    public void executeEntity(Entity entity, JsonObject definition) {
        MxtKubeJsDataCodec.decodeCached(EntityAction.CODEC, definition, entity.level().registryAccess())
                .execute(entity, FormulaContext.of(entity));
    }

    @Info("Decodes and executes any registered bi-entity action definition.")
    public void executeBiEntity(Entity actor, Entity target, JsonObject definition) {
        MxtKubeJsDataCodec.decodeCached(BiEntityAction.CODEC, definition, actor.level().registryAccess())
                .execute(actor, target, FormulaContext.of(actor));
    }

    @Info("Decodes and executes any registered block action definition.")
    public void executeBlock(Level level, BlockPos pos, JsonObject definition) {
        MxtKubeJsDataCodec.decodeCached(BlockAction.CODEC, definition, level.registryAccess())
                .execute(level, pos, FormulaContext.of(level));
    }

    @Info("Decodes and executes any registered item action definition.")
    public void executeItem(Entity holder, ItemStack stack, JsonObject definition) {
        MxtKubeJsDataCodec.decodeCached(ItemAction.CODEC, definition, holder.level().registryAccess())
                .execute(holder, stack, FormulaContext.of(holder));
    }
}
