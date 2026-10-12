package com.iafenvoy.mxt.testmod;

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
 * Carriers for the weapon fixtures that exercise the dynamic attribute path. They are plain items on purpose: the
 * numbers under test come only from the binding, so a reader knows every point of the attribute came from there.
 */
public final class MxtTestWeaponItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);

    public static final DeferredItem<Item> DYNAMIC_BLADE = register("dynamic_blade", Item::new);
    public static final DeferredItem<Item> OFFHAND_FAN = register("offhand_fan", Item::new);
    public static final DeferredItem<Item> FORMULA_BLADE = register("formula_blade", Item::new);

    private MxtTestWeaponItems() {
    }

    private static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
    }
}
