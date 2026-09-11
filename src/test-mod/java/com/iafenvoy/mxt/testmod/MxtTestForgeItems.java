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
 * Test items that <em>carry</em> a binding, so the forge table's gated path is reachable in game.
 *
 * <h2>Why these are separate from {@link MxtTestItems}</h2>
 * The two items there are a hammer and a manual with the binding attached by hand on a stack, which is
 * enough for the scenario that pokes the config directly and useless to a player - an item off the
 * creative menu has no component and so cannot be placed on the table at all. These instead declare
 * their binding the way a real item would.
 *
 * <h2>The matrix is the point</h2>
 * One tool that unlocked everything would only ever show "everything", and the interesting rule -
 * the method list is the blueprint's {@code allowed_methods} intersected with the union across the
 * placed tools - would never be exercised. So the tools are nested:
 * <pre>
 *  crude_hammer   2 methods
 *  smith_hammer   5 methods
 *  master_hammer 10 methods
 * </pre>
 * and the two blueprints declare their allowed set differently: {@code iron_sword} lists all ten ids
 * outright, {@code pickaxe} names the three-member tag {@code #mxt_test:pickaxe_methods}. Between
 * them, a crude hammer shows two either way, and a smith hammer shows five against the list but only
 * two against the tag - which is what proves both sides of the intersection are applied.
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
     * A tool whose {@code mxt:tool_binding} is resolved from its own id.
     *
     * <p>Every one of these items follows the same rule - the item {@code mxt_test:crude_hammer} carries
     * the binding {@code mxt_test:crude_hammer} - which is what lets the binding be named as a plain
     * {@link ResourceKey} instead of a resolved holder. A holder cannot be resolved here: items are
     * built while the mod is constructed, and the datapack registries these bindings live in are not
     * loaded until the first reload. {@code delayedHolderComponent} is the mechanism for exactly this,
     * and it resolves the key once the registries exist.
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
