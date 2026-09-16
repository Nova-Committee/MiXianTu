package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.function.Function;

/**
 * Test items that carry a binding so the forge table's gated path is reachable in game: unlike
 * {@link MxtTestItems}, these declare the binding on the item itself, as a real item would. The nested
 * hammers and the two blueprints - one listing its ids, one naming a tag - exercise both sides of the
 * intersection rule, the blueprint's {@code allowed_methods} against the union across the placed tools.
 */
public final class MxtTestForgeItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);

    public static final DeferredItem<Item> CRUDE_HAMMER = tool("crude_hammer");
    public static final DeferredItem<Item> SMITH_HAMMER = tool("smith_hammer");
    public static final DeferredItem<Item> MASTER_HAMMER = tool("master_hammer");
    public static final DeferredItem<Item> SWORD_MANUAL = manual("sword_manual");
    public static final DeferredItem<Item> PICKAXE_MANUAL = manual("pickaxe_manual");

    private MxtTestForgeItems() {
    }

    /**
     * A tool whose {@code mxt:tool_binding} is named by its own id; a plain {@link ResourceKey} rather
     * than a holder, because items are built before the datapack registries these bindings live in load.
     */
    private static DeferredItem<Item> tool(String path) {
        return register(path, properties -> properties
                .delayedHolderComponent(MxtDataComponents.TOOL_BINDING.get(), key(MxtResourceKeys.TOOL_BINDING, path)));
    }

    private static DeferredItem<Item> manual(String path) {
        return register(path, properties -> properties
                .delayedHolderComponent(MxtDataComponents.BLUEPRINT_BINDING.get(), key(MxtResourceKeys.BLUEPRINT_BINDING, path)));
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
    }

    private static DeferredItem<Item> register(String path, Function<Properties, Properties> configure) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> new Item(configure.apply(new Properties().setId(key))));
    }
}
