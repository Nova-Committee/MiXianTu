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
 * The two sample cultivation manuals.
 *
 * <h2>Why these live in the test mod</h2>
 * They exist to make the technique-learning paths reachable in game, not to ship content: nothing in
 * the mod itself depends on them, and they carry no behaviour of their own - a plain {@link Item} plus
 * a datapack binding is the whole definition. Registering them on the main item registry would put two
 * unfinished props into every player's creative menu and freeze their ids into the mod's save data, so
 * they are owned by the development scenario instead.
 *
 * <h2>What the pair covers</h2>
 * {@code azure_water_manual} is the held one and {@code iron_body_manual} the instant one, which keeps
 * both branches of {@code TechniqueBinding.requiresHold} exercised at once. The held manual also
 * declares a non-default {@code hold_animation}, so the field is covered by a real fixture rather than
 * only by the codec checks in {@link MxtTestMod}.
 */
public final class MxtTestTechniqueItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);

    public static final DeferredItem<Item> AZURE_WATER_MANUAL = register("azure_water_manual");
    public static final DeferredItem<Item> IRON_BODY_MANUAL = register("iron_body_manual");

    private MxtTestTechniqueItems() {
    }

    /**
     * A plain item, matching how a technique binding finds its carrier: the binding names the item and
     * {@code ItemMixin} supplies the use behaviour of any item that declares a hold. Registered on the
     * test mod's own registry so the samples stay out of the main mod's creative menu.
     */
    private static DeferredItem<Item> register(String path) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> new Item(new Properties().setId(key)));
    }
}
