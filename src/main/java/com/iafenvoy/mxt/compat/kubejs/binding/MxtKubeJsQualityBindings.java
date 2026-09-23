package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime quality operations exposed as {@code MxtQuality}. A quality lives on an item stack, so every call names
 * the stack it acts on and the entity is only what the registry lookup runs from.
 */
public final class MxtKubeJsQualityBindings {
    @Info("The quality tier id that stack resolves to right now, or null when it has none.")
    public String get(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.itemQuality(entity, stack));
    }

    @Info("The ladder that stack's tier belongs to, or null when no single ladder holds it.")
    public String chain(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.itemQualityChain(entity, stack));
    }

    @Info("The tier one step up from the stack's own, or null at the top of its ladder.")
    public String next(Entity entity, ItemStack stack) {
        return text(MxtKubeJsApi.nextItemQuality(entity, stack));
    }

    @Info("Writes a tier onto the stack as an override; false for an unknown id or on the client.")
    public boolean set(Entity entity, ItemStack stack, String quality) {
        return MxtKubeJsApi.setItemQuality(entity, stack, id(quality));
    }

    @Info("Takes the override off the stack so it falls back to its definition's default tier.")
    public boolean clear(Entity entity, ItemStack stack) {
        return MxtKubeJsApi.clearItemQuality(entity, stack);
    }

    @Info("Moves the stack one step up its ladder, paying that step's costs; the result carries why it refused.")
    public Object upgrade(LivingEntity entity, ItemStack stack) {
        return MxtKubeJsApi.upgradeItemQuality(entity, stack);
    }

    private static String text(Identifier id) {
        return id == null ? null : id.toString();
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
