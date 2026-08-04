package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.data.ChequeData;
import com.iafenvoy.mxt.item.ChequeItem;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService.OptionalLongResult;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.screen.EconomySlots.Input;
import com.iafenvoy.mxt.screen.EconomySlots.Output;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Menu state and all authoritative cheque conversion operations.
 */
public final class ChequeTableMenu extends AbstractContainerMenu {
    private final Container currency = new SimpleContainer(15);
    private final Container chequeInput = new SimpleContainer(1);
    private final Container chequeOutput = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    public ChequeTableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ChequeTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(MxtMenus.CHEQUE_TABLE.get(), containerId);
        this.access = access;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                int index = column + row * 5;
                this.addSlot(new Input(this.currency, index, 8 + column * 18, 18 + row * 18,
                        stack -> CurrencyValueService.unitValue(stack.getItem()).isPresent()));
            }
        }
        this.addSlot(new Input(this.chequeInput, 0, 134, 18, stack -> stack.is(MxtItems.CHEQUE.get())));
        this.addSlot(new Output(this.chequeOutput, 0, 134, 54));
        this.addPlayerInventory(inventory, 84);
    }

    public boolean checkIn(Player player) {
        ItemStack blank = this.chequeInput.getItem(0);
        if (!blank.is(MxtItems.CHEQUE.get()) || blank.getOrDefault(MxtDataComponents.CHEQUE.get(), ChequeData.EMPTY).value() != 0L)
            return false;
        OptionalLongResult value = CurrencyPaymentService.collectCurrency(this.currency);
        if (!value.valid() || value.value() <= 0L || !this.chequeOutput.getItem(0).isEmpty()) return false;
        this.chequeOutput.setItem(0, ChequeItem.create(value.value(), player.getGameProfile().name()));
        blank.shrink(1);
        this.currency.clearContent();
        this.broadcastChanges();
        return true;
    }

    public boolean checkOut() {
        ItemStack cheque = this.chequeInput.getItem(0);
        ChequeData data = cheque.getOrDefault(MxtDataComponents.CHEQUE.get(), ChequeData.EMPTY);
        if (!cheque.is(MxtItems.CHEQUE.get()) || data.value() <= 0L || !this.currency.isEmpty()) return false;
        List<ItemStack> change = CurrencyPaymentService.makeChange(data.value()).orElse(null);
        if (change == null || change.size() > this.currency.getContainerSize()) return false;
        for (int index = 0; index < change.size(); index++) this.currency.setItem(index, change.get(index));
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
        this.clearContainer(player, this.currency);
        this.clearContainer(player, this.chequeInput);
    }

    private void addPlayerInventory(Inventory inventory, int y) {
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, y + row * 18));
        for (int column = 0; column < 9; column++) this.addSlot(new Slot(inventory, column, 8 + column * 18, y + 58));
    }
}
