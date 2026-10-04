package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.screen.EconomySlots.Display;
import com.iafenvoy.mxt.screen.EconomySlots.Ghost;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.util.InventoryUtil;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Server-authoritative menus for both configurable station blocks. The four menu
 * types preserve the server-selected permissions while sharing one implementation.
 */
public final class StationMenu extends ApricityContainerMenu {
    public enum Mode {
        SYSTEM_OWNER, SYSTEM_CUSTOMER, TRADE_OWNER, TRADE_CUSTOMER;

        public boolean isSystem() {
            return this == SYSTEM_OWNER || this == SYSTEM_CUSTOMER;
        }

        public boolean isOwner() {
            return this == SYSTEM_OWNER || this == TRADE_OWNER;
        }
    }

    /**
     * The ranges of the grouped layout, in the order the page declares its containers: the two 12-cell templates,
     * the owner-only display cell, the owner's 21 stock cells, then the player inventory. Shift-click only ever
     * moves between the stock and the player inventory; the templates and the display cell stay where they are.
     */
    private static final int TEMPLATE_CELLS = 12, TEMPLATES_END = TEMPLATE_CELLS * 2;
    private static final int STOCK_START = TEMPLATES_END + 1, STOCK_SLOTS = 21, PLAYER_START = STOCK_START + STOCK_SLOTS;

    private final Mode mode;
    private final Container costs;
    private final Container rewards;
    private final @Nullable Container stock;
    private final ContainerLevelAccess access;

    public StationMenu(Mode mode, int containerId, Inventory inventory) {
        this(mode, containerId, inventory, new SimpleContainer(12), new SimpleContainer(12), mode == Mode.TRADE_OWNER ? new SimpleContainer(21) : null, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    public StationMenu(Mode mode, int containerId, Inventory inventory, Container costs, Container rewards, @Nullable Container stock, ContainerLevelAccess access) {
        this(mode, containerId, inventory, costs, rewards, stock, new SimpleContainer(1), access);
    }

    public StationMenu(Mode mode, int containerId, Inventory inventory, Container costs, Container rewards,
                       @Nullable Container stock, Container display, ContainerLevelAccess access) {
        this(mode, containerId, inventory, costs, rewards, stock, display, access, new Setup(mode, costs, rewards, stock, display));
    }

    private StationMenu(Mode mode, int containerId, Inventory inventory, Container costs, Container rewards,
                        @Nullable Container stock, Container display, ContainerLevelAccess access, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        checkContainerSize(costs, 12);
        checkContainerSize(rewards, 12);
        checkContainerSize(display, 1);
        if (stock != null) checkContainerSize(stock, 21);
        this.mode = mode;
        this.costs = costs;
        this.rewards = rewards;
        this.stock = stock;
        this.access = access;
    }

    /**
     * The menu type ApricityUI's base menu would report is its own; the open packet carries whatever this answers,
     * and the client picks its screen factory from that.
     */
    @Override
    public @NonNull MenuType<?> getType() {
        return typeFor(this.mode);
    }

    public Mode mode() {
        return this.mode;
    }

    public boolean isCustomer() {
        return !this.mode.isOwner();
    }

    /**
     * Performs one offer after proving each inventory mutation can complete.
     */
    public boolean trade(Player player) {
        if (!this.isCustomer()) return false;
        Inventory playerInventory = player.getInventory();
        Container playerPreview = InventoryUtil.copy(playerInventory);
        if (!InventoryUtil.removeItems(playerPreview, this.costs)) {
            player.sendSystemMessage(Component.translatable("screen.mxt.failure.no_enough_money"));
            return false;
        }
        if (!InventoryUtil.insertItems(playerPreview, this.rewards)) {
            player.sendSystemMessage(Component.translatable("screen.mxt.failure.no_enough_space"));
            return false;
        }

        if (this.mode == Mode.TRADE_CUSTOMER) {
            if (this.stock == null) return false;
            Container stockPreview = InventoryUtil.copy(this.stock);
            if (!InventoryUtil.removeItems(stockPreview, this.rewards)) {
                player.sendSystemMessage(Component.translatable("screen.mxt.failure.no_enough_goods"));
                return false;
            }
            if (!InventoryUtil.insertItems(stockPreview, this.costs)) {
                player.sendSystemMessage(Component.translatable("screen.mxt.failure.no_enough_space"));
                return false;
            }
            InventoryUtil.removeItems(this.stock, this.rewards);
            InventoryUtil.insertItems(this.stock, this.costs);
        }

        InventoryUtil.removeItems(playerInventory, this.costs);
        InventoryUtil.insertItems(playerInventory, this.rewards);
        this.broadcastChanges();
        return true;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        if (this.mode != Mode.TRADE_OWNER || index < STOCK_START || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < PLAYER_START) {
            if (!this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), STOCK_START, PLAYER_START, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, this.mode.isSystem() ? MxtBlocks.SYSTEM_STATION.get() : MxtBlocks.TRADE_STATION.get());
    }

    /**
     * Swaps the template stand-ins for the cells they stand for. ApricityUI's base menu builds every slot from its
     * own constructor, which runs before this menu's fields are set, and a {@link Ghost} has to be given the menu
     * that owns it - this override is still inside that constructor and has the reference.
     */
    @Override
    protected @NonNull Slot addSlot(@NonNull Slot slot) {
        if (slot instanceof PendingTemplate)
            slot = new Ghost(this, slot.container, slot.getSlotIndex(), slot.x, slot.y);
        return super.addSlot(slot);
    }

    private static MenuType<StationMenu> typeFor(Mode mode) {
        return switch (mode) {
            case SYSTEM_OWNER -> MxtMenus.SYSTEM_STATION_OWNER.get();
            case SYSTEM_CUSTOMER -> MxtMenus.SYSTEM_STATION_CUSTOMER.get();
            case TRADE_OWNER -> MxtMenus.TRADE_STATION_OWNER.get();
            case TRADE_CUSTOMER -> MxtMenus.TRADE_STATION_CUSTOMER.get();
        };
    }

    /**
     * A template cell that does not know its menu yet; see {@link #addSlot}.
     */
    private static final class PendingTemplate extends Slot {
        private PendingTemplate(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
    }

    /**
     * The containers the pages bind, the layout they are declared in and the sources its slots are built from.
     * The containers have to exist before the menu does, so the public constructors build this and hand it to
     * the private one; the layout also names the page the client half opens.
     */
    private static final class Setup {
        private final PageSlots.Layout page;

        private Setup(Mode mode, Container costs, Container rewards, @Nullable Container stock, Container display) {
            // The owner page has a stock container; without it the page would open a layout it cannot bind.
            if (mode == Mode.TRADE_OWNER && stock == null)
                throw new IllegalArgumentException("Trade station owner menu requires stock");
            PageSlots slots = PageSlots.of(AuiPages.economyPage(mode == Mode.TRADE_OWNER ? "station_owner" : "station_customer"))
                    .container("costs", costs, templates(mode))
                    .container("rewards", rewards, templates(mode));
            if (mode == Mode.TRADE_OWNER)
                slots.container("display", display, PendingTemplate::new).container("stock", stock, Slot::new);
            this.page = slots.player("inventory").build();
        }

        /**
         * The two template groups: editable ghost cells for every owner menu, read-only previews for customers.
         */
        private static PageSlots.SlotFactory templates(Mode mode) {
            if (mode.isOwner()) return PendingTemplate::new;
            return Display::new;
        }

        private PageSlots.Layout page() {
            return this.page;
        }
    }
}
