package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.screen.EconomySlots.Display;
import com.iafenvoy.mxt.screen.EconomySlots.Ghost;
import com.iafenvoy.mxt.util.InventoryUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative menus for both configurable station blocks. The four menu
 * types preserve the server-selected permissions while sharing one implementation.
 */
public final class StationMenu extends AbstractContainerMenu {
    public enum Mode {
        SYSTEM_OWNER, SYSTEM_CUSTOMER, TRADE_OWNER, TRADE_CUSTOMER;

        public boolean isSystem() {
            return this == SYSTEM_OWNER || this == SYSTEM_CUSTOMER;
        }

        public boolean isOwner() {
            return this == SYSTEM_OWNER || this == TRADE_OWNER;
        }
    }

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
        super(typeFor(mode), containerId);
        checkContainerSize(costs, 12);
        checkContainerSize(rewards, 12);
        checkContainerSize(display, 1);
        if (mode == Mode.TRADE_OWNER && stock == null)
            throw new IllegalArgumentException("Trade station owner menu requires stock");
        if (stock != null) checkContainerSize(stock, 21);
        this.mode = mode;
        this.costs = costs;
        this.rewards = rewards;
        this.stock = stock;
        this.access = access;
        this.addTemplates();
        if (mode == Mode.TRADE_OWNER) {
            this.addSlot(new Ghost(this, display, 0, 152, 72));
            this.addStock();
        }
        this.addPlayerInventory(inventory, mode == Mode.TRADE_OWNER ? 140 : 84);
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
        if (this.mode != Mode.TRADE_OWNER || index < 25 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < 46) {
            if (!this.moveItemStackTo(slot.getItem(), 46, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), 25, 46, false)) {
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

    private void addTemplates() {
        int templateY = this.mode == Mode.TRADE_OWNER ? 16 : 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                int index = column + row * 4;
                if (this.mode.isOwner()) {
                    this.addSlot(new Ghost(this, this.costs, index, 8 + column * 18, templateY + row * 18));
                    this.addSlot(new Ghost(this, this.rewards, index, 98 + column * 18, templateY + row * 18));
                } else {
                    this.addSlot(new Display(this.costs, index, 8 + column * 18, 18 + row * 18));
                    this.addSlot(new Display(this.rewards, index, 98 + column * 18, 18 + row * 18));
                }
            }
        }
    }

    private void addStock() {
        if (this.stock != null)
            for (int row = 0; row < 3; row++)
                for (int column = 0; column < 7; column++)
                    this.addSlot(new Slot(this.stock, column + row * 7, 8 + column * 18, 72 + row * 18));
    }

    private void addPlayerInventory(Inventory inventory, int y) {
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, y + row * 18));
        for (int column = 0; column < 9; column++) this.addSlot(new Slot(inventory, column, 8 + column * 18, y + 58));
    }

    private static MenuType<StationMenu> typeFor(Mode mode) {
        return switch (mode) {
            case SYSTEM_OWNER -> MxtMenus.SYSTEM_STATION_OWNER.get();
            case SYSTEM_CUSTOMER -> MxtMenus.SYSTEM_STATION_CUSTOMER.get();
            case TRADE_OWNER -> MxtMenus.TRADE_STATION_OWNER.get();
            case TRADE_CUSTOMER -> MxtMenus.TRADE_STATION_CUSTOMER.get();
        };
    }
}
