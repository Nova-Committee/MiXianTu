package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.item.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ToolMaterial;
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

    // Declaration order is the creative tab order: the tab walks the registry entries, which are handed back in
    // registration order. Keep the list grouped the way the tab should read. Block items follow all of these (they
    // are registered from MxtBlocks, which is initialised after this class) and then the drained spirit stones.
    // Currency
    public static final DeferredItem<Item> COPPER_COIN = register("copper_coin", Item::new);
    public static final DeferredItem<Item> IRON_COIN = register("iron_coin", Item::new);
    public static final DeferredItem<Item> GOLD_COIN = register("gold_coin", Item::new);
    public static final DeferredItem<Item> DIAMOND_COIN = register("diamond_coin", Item::new);
    public static final DeferredItem<Item> EMERALD_COIN = register("emerald_coin", Item::new);
    public static final DeferredItem<Item> NETHERITE_COIN = register("netherite_coin", Item::new);

    // Spirit stones
    public static final DeferredItem<SpiritStoneItem> SPIRIT_STONE = register("spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> MEDIUM_SPIRIT_STONE = register("medium_spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> HIGH_SPIRIT_STONE = register("high_spirit_stone", SpiritStoneItem::new);
    public static final DeferredItem<SpiritStoneItem> SUPREME_SPIRIT_STONE = register("supreme_spirit_stone", SpiritStoneItem::new);

    // Materials
    public static final DeferredItem<Item> SPIRIT_IRON_INGOT = register("spirit_iron_ingot", Item::new);
    public static final DeferredItem<Item> SPIRIT_IRON_NUGGET = register("spirit_iron_nugget", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD = register("spirit_wood", Item::new);
    public static final DeferredItem<Item> SPIRIT_WOOD_CORE = register("spirit_wood_core", Item::new);

    // Spirit root, physique and technique
    public static final DeferredItem<SpiritRootItem> SPIRIT_ROOT = register("spirit_root", SpiritRootItem::new);
    public static final DeferredItem<PhysiqueItem> PHYSIQUE = register("physique", PhysiqueItem::new);
    public static final DeferredItem<Item> CULTIVATION_JADE_SLIP = register("cultivation_jade_slip", Item::new);

    // Containers and tools
    public static final DeferredItem<Item> SPIRIT_RING = register("spirit_ring", Item::new);
    public static final DeferredItem<Item> SPIRIT_STONE_BAG = register("spirit_stone_bag", Item::new);
    public static final DeferredItem<SpiritVesselItem> SPIRIT_VESSEL = register("spirit_vessel", properties -> new SpiritVesselItem(properties.stacksTo(1).component(MxtDataComponents.RESOURCE_CONTAINER, ResourceContainerComponent.EMPTY)));
    public static final DeferredItem<IdentificationMirrorItem> IDENTIFICATION_MIRROR = register("identification_mirror", properties -> new IdentificationMirrorItem(properties.stacksTo(1)));

    // The talisman chain in the order a carrier is made: paper, brush, pigment, carrier, recall
    public static final DeferredItem<TalismanBrushItem> TALISMAN_BRUSH = register("talisman_brush", properties -> new TalismanBrushItem(properties.stacksTo(1)));
    public static final DeferredItem<Item> CINNABAR = register("cinnabar", Item::new);
    public static final DeferredItem<Item> BLANK_TALISMAN = register("blank_talisman", Item::new);
    public static final DeferredItem<TalismanItem> TALISMAN = register("talisman", properties -> new TalismanItem(properties.component(MxtDataComponents.TALISMAN, TalismanComponent.EMPTY)));
    public static final DeferredItem<Item> RECALL_TALISMAN = register("recall_talisman", Item::new);

    // Alchemy
    public static final DeferredItem<PillItem> PILL = register("pill", PillItem::new);
    public static final DeferredItem<Item> ALCHEMY_DREGS = register("alchemy_dregs", Item::new);
    public static final DeferredItem<Item> IMPURITY = register("impurity", Item::new);

    // Contracts and beasts
    public static final DeferredItem<ContractScrollItem> CONTRACT_SCROLL = register("contract_scroll", properties -> new ContractScrollItem(properties.component(MxtDataComponents.CONTRACT_SCROLL, ContractScrollComponent.EMPTY)));
    public static final DeferredItem<BeastTamingBellItem> BEAST_TAMING_BELL = register("beast_taming_bell", BeastTamingBellItem::new);
    public static final DeferredItem<SpiritBeastBagItem> SPIRIT_BEAST_BAG = register("spirit_beast_bag", properties -> new SpiritBeastBagItem(properties.stacksTo(1).component(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY)));

    // Formations, secret realms and the tokens they share
    public static final DeferredItem<FormationPlateItem> FORMATION_PLATE = register("formation_plate", properties -> new FormationPlateItem(properties.stacksTo(1).component(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY)));
    public static final DeferredItem<SecretRealmTokenItem> SECRET_REALM_TOKEN = register("secret_realm_token", properties -> new SecretRealmTokenItem(properties.stacksTo(1).component(MxtDataComponents.SECRET_REALM_TOKEN, SecretRealmTokenComponent.EMPTY)));
    public static final DeferredItem<Item> SECRET_REALM_REWARD_BOX = register("secret_realm_reward_box", Item::new);
    public static final DeferredItem<RiftAnchorItem> RIFT_ANCHOR = register("rift_anchor", properties -> new RiftAnchorItem(properties.stacksTo(1).component(MxtDataComponents.RIFT, RiftComponent.EMPTY)));
    public static final DeferredItem<TokenItem> WOODEN_TOKEN = register("wooden_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenComponent.EMPTY)));
    public static final DeferredItem<TokenItem> STONE_TOKEN = register("stone_token", properties -> new TokenItem(properties.component(MxtDataComponents.TOKEN, TokenComponent.EMPTY)));

    // Economy
    public static final DeferredItem<ChequeItem> CHEQUE = register("cheque", ChequeItem::new);

    // An easter egg, not framework: a plain vanilla food, so eating it, the nutrition and the stack behaviour are
    // all vanilla's; the second argument is how many tooltip lines its language keys carry.
    public static final DeferredItem<SpecialItem> FRIED_DOUGH_CAKE = register("fried_dough_cake",
            properties -> new SpecialItem(properties.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build()), 1));
    // The other easter egg: a netherite sword whose damage is a step above a netherite sword's, plus one red line.
    public static final DeferredItem<SpecialItem> SKY_SWALLOWING_SWORD = register("sky_swallowing_sword",
            properties -> new SpecialItem(properties.sword(ToolMaterial.NETHERITE, 5.0F, -2.4F), 1, ChatFormatting.RED));

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
