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

/** Items owned only by the development scenario. */
public final class MxtTestItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MxtTestMod.MOD_ID);
    public static final DeferredItem<Item> QINGXIAO_SPIRIT_CRYSTAL = register("qingxiao_spirit_crystal", Item::new);

    private MxtTestItems() {
    }

    private static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path));
        return REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
    }
}
