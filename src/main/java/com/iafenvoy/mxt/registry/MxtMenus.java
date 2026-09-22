package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.menu.*;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    /**
     * An artifact's own storage. A chest menu rather than a menu of our own, because that is exactly what it is:
     * nine slots a row over a container. The row count travels with the open packet, and the client's screen is
     * the vanilla container screen, so there is nothing here to draw.
     *
     * <p>Whoever opens it has to send that count - {@code openMenu(provider, writer)} - because the client builds
     * the same menu shape from it; the slots themselves arrive through the menu sync.</p>
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> ARTIFACT_STORAGE = REGISTRY.register("artifact_storage",
            () -> IMenuTypeExtension.<ChestMenu>create((containerId, inventory, buffer) -> {
                int rows = buffer.readVarInt();
                // Qualified on purpose: a bare name here would be a self-reference in an initializer, and this
                // factory only runs once a menu is opened, long after the holder is bound.
                return new ChestMenu(MxtMenus.ARTIFACT_STORAGE.get(), containerId, inventory, new SimpleContainer(rows * 9), rows);
            }));

    private static DeferredHolder<MenuType<?>, MenuType<StationMenu>> station(String name, Mode mode) {
        return REGISTRY.register(name, () -> new MenuType<>((containerId, inventory) -> new StationMenu(mode, containerId, inventory), FeatureFlags.VANILLA_SET));
    }
}
