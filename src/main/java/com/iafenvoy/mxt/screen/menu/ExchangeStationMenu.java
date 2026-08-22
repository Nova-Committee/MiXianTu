package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService.ExchangeOffer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * A stonecutter-style selector for one-way, data-driven currency exchanges.
 */
public final class ExchangeStationMenu extends AbstractContainerMenu {
    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = 38;
    private final ContainerLevelAccess access;
    private final RegistryAccess registryAccess;
    private final Player owner;
    private final DataSlot selectedExchange = DataSlot.standalone();
    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ExchangeStationMenu.this.slotsChanged(this);
            ExchangeStationMenu.this.updateListener.run();
        }
    };
    private final Container result = new SimpleContainer(1);
    private final Slot inputSlot;
    private final Slot resultSlot;
    private ItemStack previousInput = ItemStack.EMPTY;
    private List<ExchangeOffer> offers = List.of();
    private Runnable updateListener = () -> {
    };
    private long lastSoundTime;

    public ExchangeStationMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ExchangeStationMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(MxtMenus.EXCHANGE_STATION.get(), containerId);
        this.access = access;
        this.registryAccess = inventory.player.level().registryAccess();
        this.owner = inventory.player;
        this.selectedExchange.set(-1);
        this.inputSlot = this.addSlot(new Slot(this.input, 0, 20, 33) {
            @Override
            public boolean mayPlace(@NonNull ItemStack stack) {
                return CurrencyValueService.isExchangeInput(ExchangeStationMenu.this.registryAccess, ExchangeStationMenu.this.owner, stack);
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.result, 0, 143, 33) {
            @Override
            public boolean mayPlace(@NonNull ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(@NonNull Player player, @NonNull ItemStack stack) {
                ExchangeOffer offer = ExchangeStationMenu.this.selectedOffer();
                if (offer == null || ExchangeStationMenu.this.inputSlot.getItem().getCount() < offer.cost()) return;
                stack.onCraftedBy(player, stack.getCount());
                ExchangeStationMenu.this.inputSlot.remove(offer.cost());
                ExchangeStationMenu.this.setupResultSlot(ExchangeStationMenu.this.selectedExchange.get());
                ExchangeStationMenu.this.playTakeSound();
                super.onTake(player, stack);
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.selectedExchange);
    }

    public int getSelectedExchange() {
        return this.selectedExchange.get();
    }

    public List<ExchangeOffer> getVisibleOffers() {
        return this.offers;
    }

    public int getNumberOfVisibleOffers() {
        return this.offers.size();
    }

    public boolean hasInputItem() {
        return this.inputSlot.hasItem() && !this.offers.isEmpty();
    }

    public void registerUpdateListener(Runnable listener) {
        this.updateListener = listener;
    }

    @Override
    public boolean clickMenuButton(@NonNull Player player, int buttonId) {
        if (this.selectedExchange.get() == buttonId) return false;
        if (!this.isValidExchange(buttonId)) return false;
        this.selectedExchange.set(buttonId);
        this.setupResultSlot(buttonId);
        return true;
    }

    @Override
    public void slotsChanged(@NonNull Container changed) {
        ItemStack current = this.inputSlot.getItem();
        if (!ItemStack.isSameItemSameComponents(current, this.previousInput)) {
            this.previousInput = current.copy();
            this.setupOfferList(current);
        } else {
            this.setupResultSlot(this.selectedExchange.get());
        }
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex == RESULT_SLOT) {
            Item item = stack.getItem();
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
            item.onCraftedBy(stack, player);
            slot.onQuickCraft(stack, original);
        } else if (slotIndex == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) return ItemStack.EMPTY;
        } else if (CurrencyValueService.isExchangeInput(this.registryAccess, player, stack)) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, RESULT_SLOT, false)) return ItemStack.EMPTY;
        } else if (slotIndex < 29) {
            if (!this.moveItemStackTo(stack, 29, INVENTORY_END, false)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(stack, INVENTORY_START, 29, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (slotIndex == RESULT_SLOT) player.drop(stack, false);
        this.broadcastChanges();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, MxtBlocks.EXCHANGE_STATION.get());
    }

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        this.result.clearContent();
        this.access.execute((level, position) -> this.clearContainer(player, this.input));
    }

    private void setupOfferList(ItemStack stack) {
        this.selectedExchange.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        this.offers = stack.isEmpty() ? List.of() : CurrencyValueService.exchangeOffers(this.registryAccess, this.owner, stack);
        this.broadcastChanges();
    }

    private void setupResultSlot(int index) {
        ExchangeOffer offer = this.isValidExchange(index) ? this.offers.get(index) : null;
        this.resultSlot.set(offer != null && this.inputSlot.getItem().getCount() >= offer.cost() ? offer.output() : ItemStack.EMPTY);
        this.broadcastChanges();
    }

    private boolean isValidExchange(int index) {
        return index >= 0 && index < this.offers.size();
    }

    private ExchangeOffer selectedOffer() {
        int index = this.selectedExchange.get();
        return this.isValidExchange(index) ? this.offers.get(index) : null;
    }

    private void playTakeSound() {
        this.access.execute((level, position) -> {
            long time = level.getGameTime();
            if (this.lastSoundTime != time) {
                level.playSound(null, position, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.lastSoundTime = time;
            }
        });
    }
}
