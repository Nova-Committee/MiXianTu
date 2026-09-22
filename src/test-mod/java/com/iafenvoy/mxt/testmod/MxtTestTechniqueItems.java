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
 * The two sample cultivation manuals, carrying no behaviour beyond a plain {@link Item} plus a datapack
 * binding: one is held and one instant (both {@code TechniqueBinding.requiresHold} branches), the held one
 * declaring a hold_animation. Living here keeps two unfinished props out of the creative menu.
 */
public final class MxtTestTechniqueItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);

    public static final DeferredItem<Item> AZURE_WATER_MANUAL = register("azure_water_manual");
    public static final DeferredItem<Item> IRON_BODY_MANUAL = register("iron_body_manual");

    private MxtTestTechniqueItems() {
    }

    // A technique binding finds its carrier by name, and the click path supplies the use behaviour of any item
    // that declares a hold, so no subclass is needed.
    private static DeferredItem<Item> register(String path) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> new Item(new Properties().setId(key)));
    }
}
