package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.iafenvoy.mxt.screen.menu.ExchangeStationMenu;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Server-authoritative economy menus.
 */
public final class MxtMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, MiXianTu.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ExchangeStationMenu>> EXCHANGE_STATION = REGISTRY.register("exchange_station", () -> new MenuType<>(ExchangeStationMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<ChequeTableMenu>> CHEQUE_TABLE = REGISTRY.register("cheque_table", () -> new MenuType<>(ChequeTableMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> SYSTEM_STATION_OWNER = station("system_station_owner", Mode.SYSTEM_OWNER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> SYSTEM_STATION_CUSTOMER = station("system_station_customer", Mode.SYSTEM_CUSTOMER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> TRADE_STATION_OWNER = station("trade_station_owner", Mode.TRADE_OWNER);
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> TRADE_STATION_CUSTOMER = station("trade_station_customer", Mode.TRADE_CUSTOMER);
    public static final DeferredHolder<MenuType<?>, MenuType<PlayerTradeMenu>> PLAYER_TRADE = REGISTRY.register("player_trade", () -> IMenuTypeExtension.create(PlayerTradeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SpiritCraftingMenu>> SPIRIT_CRAFTING_TABLE = REGISTRY.register("spirit_crafting_table", () -> IMenuTypeExtension.create(SpiritCraftingMenu::new));

    private static DeferredHolder<MenuType<?>, MenuType<StationMenu>> station(String name, Mode mode) {
        return REGISTRY.register(name, () -> new MenuType<>((containerId, inventory) -> new StationMenu(mode, containerId, inventory), FeatureFlags.VANILLA_SET));
    }

    private MxtMenus() {
    }
}
