package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class MxtItems {
    public static final Items REGISTRY = DeferredRegister.createItems(MiXianTu.MOD_ID);

    public static final DeferredItem<Item> COPPER_COIN = register("copper_coin", Item::new);
    public static final DeferredItem<Item> IRON_COIN = register("iron_coin", Item::new);
    public static final DeferredItem<Item> GOLD_COIN = register("gold_coin", Item::new);
    public static final DeferredItem<Item> DIAMOND_COIN = register("diamond_coin", Item::new);
    public static final DeferredItem<Item> EMERALD_COIN = register("emerald_coin", Item::new);
    public static final DeferredItem<Item> NETHERITE_COIN = register("netherite_coin", Item::new);
    public static final DeferredItem<SpiritStoneItem> SPIRIT_STONE = register("spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> MEDIUM_SPIRIT_STONE = register("medium_spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> HIGH_SPIRIT_STONE = register("high_spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> SUPREME_SPIRIT_STONE = register("supreme_spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<Item> SPIRIT_IRON_INGOT = register("spirit_iron_ingot", Item::new);
    public static final DeferredItem<Item> SPIRIT_IRON_NUGGET = register("spirit_iron_nugget", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD = register("spirit_wood", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD_CORE = register("spirit_wood_core", Item::new);
    public static final DeferredItem<Item> SPIRIT_RING = register("spirit_ring", Item::new);
    public static final DeferredItem<Item> SPIRIT_STONE_BAG = register("spirit_stone_bag", Item::new);
    public static final DeferredItem<TokenItem> WOODEN_TOKEN = register("wooden_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenComponent.EMPTY)));
    public static final DeferredItem<TokenItem> STONE_TOKEN = register("stone_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenComponent.EMPTY)));
    public static final DeferredItem<Item> SPIRIT_ROOT = register("spirit_root", Item::new);
    public static final DeferredItem<Item> CULTIVATION_JADE_SLIP = register("cultivation_jade_slip", Item::new);
    public static final DeferredItem<Item> BLANK_TALISMAN_PAPER = register("blank_talisman_paper", Item::new);
    public static final DeferredItem<Item> ALCHEMY_DREGS = register("alchemy_dregs", Item::new);
    public static final DeferredItem<Item> IMPURITY = register("impurity", Item::new);
    public static final DeferredItem<ContractScrollItem> CONTRACT_SCROLL = register("contract_scroll", properties -> new ContractScrollItem(properties.component(MxtDataComponents.CONTRACT_SCROLL, ContractScrollComponent.EMPTY)));
    public static final DeferredItem<Item> RECALL_TALISMAN = register("recall_talisman", Item::new);
    public static final DeferredItem<BeastTamingBellItem> BEAST_TAMING_BELL = register("beast_taming_bell", BeastTamingBellItem::new);
    public static final DeferredItem<Item> REALM_REWARD_BOX = register("realm_reward_box", Item::new);
    public static final DeferredItem<SpiritBeastBagItem> SPIRIT_BEAST_BAG = register("spirit_beast_bag", properties -> new SpiritBeastBagItem(properties.stacksTo(1).component(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY)));
    public static final DeferredItem<FormationPlateItem> FORMATION_PLATE = register("formation_plate", properties -> new FormationPlateItem(properties.stacksTo(1).component(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY)));
    public static final DeferredItem<RealmTokenItem> REALM_TOKEN = register("realm_token", properties -> new RealmTokenItem(properties.stacksTo(1).component(MxtDataComponents.REALM_TOKEN, RealmTokenComponent.EMPTY)));
    public static final DeferredItem<SpiritVesselItem> SPIRIT_VESSEL = register("spirit_vessel", properties -> new SpiritVesselItem(properties.stacksTo(1).component(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY)));
    public static final DeferredItem<IdentificationMirrorItem> IDENTIFICATION_MIRROR = register("identification_mirror", properties -> new IdentificationMirrorItem(properties.stacksTo(1)));
    public static final DeferredItem<Item> TALISMAN_BRUSH = register("talisman_brush", Item::new);
    public static final DeferredItem<Item> TALISMAN_INK = register("talisman_ink", Item::new);
    public static final DeferredItem<ChequeItem> CHEQUE = register("cheque", ChequeItem::new);

    public static <T extends Item> DeferredItem<T> register(String path, Function<Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path));
        return REGISTRY.register(path, () -> factory.apply(new Properties().setId(key)));
    }

    public static DeferredItem<BlockItem> registerBlockItem(String path, Supplier<? extends Block> block) {
        return register(path, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static Collection<DeferredHolder<Item, ? extends Item>> registeredItems() {
        return REGISTRY.getEntries();
    }

    public static List<DeferredItem<SpiritStoneItem>> spiritStones() {
        return List.of(SPIRIT_STONE, MEDIUM_SPIRIT_STONE, HIGH_SPIRIT_STONE, SUPREME_SPIRIT_STONE);
    }
}
