package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.item.DatapackItem;
import com.iafenvoy.mxt.data.item.DatapackItemReference;
import com.iafenvoy.mxt.item.ChequeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Code-owned economy items. Currency denominations themselves are defined by datapacks.
 */
public final class MxtItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MiXianTu.MOD_ID);
    private static final List<DeferredItem<? extends Item>> REGISTERED_ITEMS = new ArrayList<>();
    public static final DeferredItem<Item> COPPER_COIN = register("copper_coin", Item::new);
    public static final DeferredItem<Item> IRON_COIN = register("iron_coin", Item::new);
    public static final DeferredItem<Item> GOLD_COIN = register("gold_coin", Item::new);
    public static final DeferredItem<Item> DIAMOND_COIN = register("diamond_coin", Item::new);
    public static final DeferredItem<Item> EMERALD_COIN = register("emerald_coin", Item::new);
    public static final DeferredItem<Item> NETHERITE_COIN = register("netherite_coin", Item::new);
    public static final DeferredItem<ChequeItem> CHEQUE = register("cheque", ChequeItem::new);

    /**
     * Creates properties with the final item key before the item constructor
     * runs. NeoForge requires every item to declare this key.
     */
    public static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        DeferredItem<T> item = REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
        REGISTERED_ITEMS.add(item);
        return item;
    }

    /**
     * Registers one code-owned item whose default stack points at an
     * {@code mxt:item} entry. The binding entry, not the item class, connects
     * the physical item to that definition and conventional resource-pack model.
     */
    public static <T extends Item> DeferredItem<T> registerDataDriven(String path, Identifier binding,
                                                                                               Function<Properties, T> factory) {
        return registerDataDriven(path, DatapackItemReference.other(binding), factory);
    }

    /**
     * Registers a code-owned physical item with a category-qualified logical item definition.
     */
    public static <T extends Item> DeferredItem<T> registerDataDriven(String path, DatapackItemReference binding,
                                                                                               Function<Properties, T> factory) {
        return register(path, properties -> factory.apply(properties
                .component(MxtDataComponents.ITEM_DEFINITION.get(), binding)
                .component(DataComponents.ITEM_MODEL, DatapackItem.conventionalModel(binding.id()))));
    }

    /**
     * Registers a block item that intentionally shares its block's display name.
     */
    public static DeferredItem<BlockItem> registerBlockItem(String path, Supplier<? extends Block> block) {
        return register(path, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static List<DeferredItem<? extends Item>> registeredItems() {
        return List.copyOf(REGISTERED_ITEMS);
    }

    private MxtItems() {
    }
}
