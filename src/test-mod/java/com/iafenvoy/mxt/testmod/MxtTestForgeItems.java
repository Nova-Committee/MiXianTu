package com.iafenvoy.mxt.testmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

/**
 * Test items for the forge table. Each one is claimed by its own binding entry through {@code items}, which is the
 * only way an item reaches that table: a tool carries no component of its own. The blueprints exercise both sides
 * of the intersection rule - a blueprint's {@code allowed_methods} against the union across the placed tools.
 */
public final class MxtTestForgeItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);

    public static final DeferredItem<Item> CRUDE_HAMMER = item("crude_hammer");
    public static final DeferredItem<Item> SMITH_HAMMER = item("smith_hammer");
    public static final DeferredItem<Item> MASTER_HAMMER = item("master_hammer");
    public static final DeferredItem<Item> SWORD_MANUAL = item("sword_manual");
    public static final DeferredItem<Item> PICKAXE_MANUAL = item("pickaxe_manual");

    private MxtTestForgeItems() {
    }

    private static DeferredItem<Item> item(String path) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> new Item(new Properties().setId(key)));
    }
}
