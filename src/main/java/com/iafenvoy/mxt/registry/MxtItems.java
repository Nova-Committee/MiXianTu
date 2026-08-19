package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.item.ChequeItem;
import com.iafenvoy.mxt.item.BeastTamingBellItem;
import com.iafenvoy.mxt.item.ContractScrollItem;
import com.iafenvoy.mxt.item.FormationPlateItem;
import com.iafenvoy.mxt.item.IdentificationMirrorItem;
import com.iafenvoy.mxt.item.RealmTokenItem;
import com.iafenvoy.mxt.item.SpiritBeastBagItem;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import com.iafenvoy.mxt.item.TokenItem;
import com.iafenvoy.mxt.data.item.ContractScrollData;
import com.iafenvoy.mxt.data.item.FormationPlateData;
import com.iafenvoy.mxt.data.item.RealmTokenData;
import com.iafenvoy.mxt.data.item.ResourceContainerData;
import com.iafenvoy.mxt.data.item.SpiritBeastData;
import com.iafenvoy.mxt.data.item.TokenData;
import net.minecraft.core.registries.Registries;
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
 * Code-owned framework items. Currency denominations and gameplay bindings remain datapack-owned.
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
    public static final DeferredItem<Item> SPIRIT_STONE = register("spirit_stone", Item::new);
    public static final DeferredItem<Item> MEDIUM_SPIRIT_STONE = register("medium_spirit_stone", Item::new);
    public static final DeferredItem<Item> HIGH_SPIRIT_STONE = register("high_spirit_stone", Item::new);
    public static final DeferredItem<Item> SUPREME_SPIRIT_STONE = register("supreme_spirit_stone", Item::new);
    public static final DeferredItem<Item> SPIRIT_IRON_INGOT = register("spirit_iron_ingot", Item::new);
    public static final DeferredItem<Item> SPIRIT_IRON_NUGGET = register("spirit_iron_nugget", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD = register("spirit_wood", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD_CORE = register("spirit_wood_core", Item::new);
    public static final DeferredItem<Item> BLANK_PILL = register("blank_pill", Item::new);
    public static final DeferredItem<Item> BLANK_WEAPON = register("blank_weapon", Item::new);
    public static final DeferredItem<Item> BLANK_ARTIFACT = register("blank_artifact", Item::new);
    public static final DeferredItem<Item> SPIRIT_RING = register("spirit_ring", Item::new);
    public static final DeferredItem<Item> STORAGE_BAG = register("storage_bag", Item::new);
    public static final DeferredItem<TokenItem> WOODEN_TOKEN = register("wooden_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenData.EMPTY)));
    public static final DeferredItem<TokenItem> STONE_TOKEN = register("stone_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenData.EMPTY)));
    public static final DeferredItem<Item> SPIRIT_ROOT = register("spirit_root", Item::new);
    public static final DeferredItem<Item> CULTIVATION_JADE_SLIP = register("cultivation_jade_slip", Item::new);
    public static final DeferredItem<Item> BLANK_TALISMAN_PAPER = register("blank_talisman_paper", Item::new);
    public static final DeferredItem<Item> ALCHEMY_DREGS = register("alchemy_dregs", Item::new);
    public static final DeferredItem<Item> IMPURITY = register("impurity", Item::new);
    public static final DeferredItem<ContractScrollItem> CONTRACT_SCROLL = register("contract_scroll", properties -> new ContractScrollItem(properties.component(MxtDataComponents.CONTRACT_SCROLL, ContractScrollData.EMPTY)));
    public static final DeferredItem<Item> RECALL_TALISMAN = register("recall_talisman", Item::new);
    public static final DeferredItem<BeastTamingBellItem> BEAST_TAMING_BELL = register("beast_taming_bell", BeastTamingBellItem::new);
    public static final DeferredItem<Item> REALM_REWARD_BOX = register("realm_reward_box", Item::new);
    public static final DeferredItem<SpiritBeastBagItem> SPIRIT_BEAST_BAG = register("spirit_beast_bag", properties -> new SpiritBeastBagItem(properties.stacksTo(1).component(MxtDataComponents.SPIRIT_BEAST, SpiritBeastData.EMPTY)));
    public static final DeferredItem<FormationPlateItem> FORMATION_PLATE = register("formation_plate", properties -> new FormationPlateItem(properties.stacksTo(1).component(MxtDataComponents.FORMATION_PLATE, FormationPlateData.EMPTY)));
    public static final DeferredItem<RealmTokenItem> REALM_TOKEN = register("realm_token", properties -> new RealmTokenItem(properties.stacksTo(1).component(MxtDataComponents.REALM_TOKEN, RealmTokenData.EMPTY)));
    public static final DeferredItem<SpiritVesselItem> SPIRIT_VESSEL = register("spirit_vessel", properties -> new SpiritVesselItem(properties.stacksTo(1).component(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerData.EMPTY)));
    public static final DeferredItem<IdentificationMirrorItem> IDENTIFICATION_MIRROR = register("identification_mirror", properties -> new IdentificationMirrorItem(properties.stacksTo(1)));
    public static final DeferredItem<Item> TALISMAN_BRUSH = register("talisman_brush", Item::new);
    public static final DeferredItem<Item> TALISMAN_INK = register("talisman_ink", Item::new);
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
     * Registers a block item that intentionally shares its block's display name.
     */
    public static DeferredItem<BlockItem> registerBlockItem(String path, Supplier<? extends Block> block) {
        return register(path, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static List<DeferredItem<? extends Item>> registeredItems() {
        return REGISTERED_ITEMS;
    }

    private MxtItems() {
    }
}
