package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.data.economy.ChequeComponent;
import com.iafenvoy.mxt.item.ChequeItem;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.screen.EconomySlots.Input;
import com.iafenvoy.mxt.screen.EconomySlots.Output;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Menu state and all authoritative cheque conversion operations.
 */
public final class ChequeTableMenu extends ApricityContainerMenu {
    private final Setup setup;
    private final ContainerLevelAccess access;

    public ChequeTableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ChequeTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        this(containerId, inventory, access, new Setup(inventory));
    }

    private ChequeTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        this.access = access;
        this.setup = setup;
    }

    /**
     * The menu type ApricityUI's base menu would report is its own; the open packet carries whatever this answers,
     * and the client picks its screen factory from that.
     */
    @Override
    public MenuType<?> getType() {
        return MxtMenus.CHEQUE_TABLE.get();
    }

    public boolean checkIn(Player player) {
        ItemStack blank = this.setup.chequeInput().getItem(0);
        if (!blank.is(MxtItems.CHEQUE.get()) || blank.getOrDefault(MxtDataComponents.CHEQUE.get(), ChequeComponent.EMPTY).value() != 0L)
            return false;
        OptionalLong value = CurrencyPaymentService.collectCurrency(this.setup.currency(), player);
        if (value.isEmpty() || value.getAsLong() <= 0L || !this.setup.chequeOutput().getItem(0).isEmpty()) return false;
        this.setup.chequeOutput().setItem(0, ChequeItem.create(value.getAsLong(), player.getGameProfile().name()));
        blank.shrink(1);
        this.setup.currency().clearContent();
        this.broadcastChanges();
        return true;
    }

    public boolean checkOut() {
        ItemStack cheque = this.setup.chequeInput().getItem(0);
        ChequeComponent data = cheque.getOrDefault(MxtDataComponents.CHEQUE.get(), ChequeComponent.EMPTY);
        if (!cheque.is(MxtItems.CHEQUE.get()) || data.value() <= 0L || !this.setup.currency().isEmpty()) return false;
        List<ItemStack> change = CurrencyPaymentService.makeChange(data.value()).orElse(null);
        if (change == null || change.size() > this.setup.currency().getContainerSize()) return false;
        for (int index = 0; index < change.size(); index++) this.setup.currency().setItem(index, change.get(index));
        cheque.shrink(1);
        this.broadcastChanges();
        return true;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < 17) {
            if (!this.moveItemStackTo(slot.getItem(), 17, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), 0, 16, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, MxtBlocks.CHEQUE_TABLE.get());
    }

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        this.clearContainer(player, this.setup.currency());
        this.clearContainer(player, this.setup.chequeInput());
    }

    /**
     * The page's containers and the layout they are opened with; the containers have to exist before the menu, so
     * the public constructors build this and hand it to the private one.
     */
    private static final class Setup {
        private final SimpleContainer currency = new SimpleContainer(15);
        private final SimpleContainer chequeInput = new SimpleContainer(1);
        private final SimpleContainer chequeOutput = new SimpleContainer(1);
        private final PageSlots.Layout page;

        private Setup(Inventory inventory) {
            this.page = PageSlots.of(AuiPages.economyPage("cheque"))
                    .container("currency", this.currency, (container, index, x, y) ->
                            new Input(container, index, x, y, stack -> this.hasValue(inventory, stack)))
                    .container("cheque_in", this.chequeInput, (container, index, x, y) ->
                            new Input(container, index, x, y, stack -> stack.is(MxtItems.CHEQUE.get())))
                    .container("cheque_out", this.chequeOutput, (container, index, x, y) ->
                            new Output(container, index, x, y))
                    .player("inventory")
                    .build();
        }

        private boolean hasValue(Inventory inventory, ItemStack stack) {
            OptionalLong value = CurrencyValueService.unitValue(inventory.player.level().registryAccess(), inventory.player, stack);
            return value.isPresent() && value.getAsLong() > 0L;
        }

        private PageSlots.Layout page() {
            return this.page;
        }

        private SimpleContainer currency() {
            return this.currency;
        }

        private SimpleContainer chequeInput() {
            return this.chequeInput;
        }

        private SimpleContainer chequeOutput() {
            return this.chequeOutput;
        }
    }
}
