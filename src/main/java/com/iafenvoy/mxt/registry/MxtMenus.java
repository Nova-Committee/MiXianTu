package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.gui.*;
import com.iafenvoy.mxt.screen.menu.*;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(Dist.CLIENT)
public final class MxtMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, MiXianTu.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ExchangeStationMenu>> EXCHANGE_STATION = REGISTRY.register("exchange_station", () -> new MenuType<>(ExchangeStationMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<ChequeTableMenu>> CHEQUE_TABLE = REGISTRY.register("cheque_table", () -> new MenuType<>(ChequeTableMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> SYSTEM_STATION_OWNER = station("system_station_owner", Mode.SYSTEM_OWNER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> SYSTEM_STATION_CUSTOMER = station("system_station_customer", Mode.SYSTEM_CUSTOMER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> TRADE_STATION_OWNER = station("trade_station_owner", Mode.TRADE_OWNER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> TRADE_STATION_CUSTOMER = station("trade_station_customer", Mode.TRADE_CUSTOMER);
    public static final DeferredHolder<MenuType<?>, MenuType<PlayerTradeMenu>> PLAYER_TRADE = REGISTRY.register("player_trade", () -> IMenuTypeExtension.create(PlayerTradeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SpiritCraftingMenu>> SPIRIT_CRAFTING_TABLE = REGISTRY.register("spirit_crafting_table", () -> new MenuType<>(SpiritCraftingMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<ForgingMenu>> FORGING_TABLE = REGISTRY.register("forging_table", () -> new MenuType<>(ForgingMenu::new, FeatureFlags.VANILLA_SET));
    // Both halves build the same menu: the station stores nothing, so the server half is handed the block's position
    // and the client half builds the same shape from the player inventory alone.
    public static final DeferredHolder<MenuType<?>, MenuType<TalismanWorkstationMenu>> TALISMAN_WORKSTATION = REGISTRY.register("talisman_workstation", () -> new MenuType<>(TalismanWorkstationMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<AlchemyFurnaceMenu>> ALCHEMY_FURNACE = REGISTRY.register("alchemy_furnace", () -> IMenuTypeExtension.create(AlchemyFurnaceMenu::new));
    // Nine slots a row over a container, so a vanilla chest menu is exactly right and there is nothing to draw.
    // The row count travels in the open packet and the client rebuilds the same shape from it, so whoever opens
    // one has to send that count through openMenu(provider, writer).
    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> ARTIFACT_STORAGE = REGISTRY.register("artifact_storage", () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> {
        int rows = buffer.readVarInt();
        // Qualified on purpose: a bare name here would be a self-reference in an initializer, and this
        // factory only runs once a menu is opened, long after the holder is bound.
        return new ChestMenu(MxtMenus.ARTIFACT_STORAGE.get(), containerId, inventory, new SimpleContainer(rows * 9), rows);
    }));

    private static DeferredHolder<MenuType<?>, MenuType<StationMenu>> station(String name, Mode mode) {
        return REGISTRY.register(name, () -> new MenuType<>((containerId, inventory) -> new StationMenu(mode, containerId, inventory), FeatureFlags.VANILLA_SET));
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        page(event, CHEQUE_TABLE.get(), (menu, inventory, title) -> new ChequeTableScreen((ChequeTableMenu) menu, inventory, title));
        page(event, EXCHANGE_STATION.get(), (menu, inventory, title) -> new ExchangeStationScreen((ExchangeStationMenu) menu, inventory, title));
        page(event, SYSTEM_STATION_OWNER.get(), (menu, inventory, title) -> new StationScreen((StationMenu) menu, inventory, title));
        page(event, SYSTEM_STATION_CUSTOMER.get(), (menu, inventory, title) -> new StationScreen((StationMenu) menu, inventory, title));
        page(event, TRADE_STATION_OWNER.get(), (menu, inventory, title) -> new StationScreen((StationMenu) menu, inventory, title));
        page(event, TRADE_STATION_CUSTOMER.get(), (menu, inventory, title) -> new StationScreen((StationMenu) menu, inventory, title));
        page(event, PLAYER_TRADE.get(), (menu, inventory, title) -> new PlayerTradeScreen((PlayerTradeMenu) menu, inventory, title));
        page(event, SPIRIT_CRAFTING_TABLE.get(), (menu, inventory, title) -> new SpiritCraftingScreen((SpiritCraftingMenu) menu, inventory, title));
        page(event, FORGING_TABLE.get(), (menu, inventory, title) -> new ForgingScreen((ForgingMenu) menu, inventory, title));
        page(event, ALCHEMY_FURNACE.get(), (menu, inventory, title) -> new AlchemyFurnaceScreen((AlchemyFurnaceMenu) menu, inventory, title));
        page(event, TALISMAN_WORKSTATION.get(), (menu, inventory, title) -> new TalismanWorkstationScreen((TalismanWorkstationMenu) menu, inventory, title));
        event.register(ARTIFACT_STORAGE.get(), ContainerScreen::new);
    }

    /**
     * Registers one page screen. The event's own bound wants a screen typed to the menu its type creates, while
     * every page screen extends ApricityUI's container screen, which is typed to ApricityUI's menu base - so that
     * bound cannot be written and the erasure is taken here instead. It is only a formality: the client hands this
     * factory the menu its own menu type has just built, which is the page's menu.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void page(RegisterMenuScreensEvent event, MenuType<?> type, MenuScreens.ScreenConstructor constructor) {
        event.register((MenuType) type, constructor);
    }
}
