package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService.ExchangeOffer;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * A stonecutter-style selector for one-way, data-driven currency exchanges.
 */
public final class ExchangeStationMenu extends ApricityContainerMenu {
    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = 38;
    private final Setup setup;
    private final ContainerLevelAccess access;
    private final RegistryAccess registryAccess;
    private final Player owner;
    private final DataSlot selectedExchange = DataSlot.standalone();
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
        this(containerId, inventory, access, new Setup(inventory));
    }

    private ExchangeStationMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        this.access = access;
        this.registryAccess = inventory.player.level().registryAccess();
        this.owner = inventory.player;
        this.setup = setup;
        // The layout builds every slot inside super(...), so nothing could hand a slot the menu before this point.
        setup.bindMenu(this);
        this.inputSlot = setup.inputSlot();
        this.resultSlot = setup.resultSlot();
        this.selectedExchange.set(-1);
        this.addDataSlot(this.selectedExchange);
    }

    /**
     * The menu type ApricityUI's own base menu reports is AUI's, while the open packet carries whatever this
     * answers and the client picks its screen factory from that.
     */
    @Override
    public MenuType<?> getType() {
        return MxtMenus.EXCHANGE_STATION.get();
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
        this.setup.result().clearContent();
        this.access.execute((level, position) -> this.clearContainer(player, this.setup.input()));
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

    /**
     * What the result cell does on a take, kept out of the slot: the slot comes from the base class' layout, so it
     * is built before this menu's own body and cannot read its state directly.
     */
    private void takeResult(Player player, ItemStack stack) {
        ExchangeOffer offer = this.selectedOffer();
        if (offer == null || this.inputSlot.getItem().getCount() < offer.cost()) return;
        stack.onCraftedBy(player, stack.getCount());
        this.inputSlot.remove(offer.cost());
        this.setupResultSlot(this.selectedExchange.get());
        this.playTakeSound();
    }

    /**
     * The page's containers and the slots its layout creates; both have to exist before the menu, so the public
     * constructors build this and hand it to the private one.
     */
    private static final class Setup {
        private final SimpleContainer input = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                Setup.this.inputChanged();
            }
        };
        private final SimpleContainer result = new SimpleContainer(1);
        private final PageSlots.Layout page;
        /**
         * Set by {@link #bindMenu} right after the menu's {@code super(...)}, which is where the slots are built;
         * a slot only ever reaches back through this.
         */
        private ExchangeStationMenu menu;
        private Slot inputSlot;
        private Slot resultSlot;

        private Setup(Inventory inventory) {
            RegistryAccess registryAccess = inventory.player.level().registryAccess();
            Player owner = inventory.player;
            this.page = PageSlots.of(AuiPages.economyPage("exchange"))
                    .container("input", this.input, (container, index, x, y) -> {
                        this.inputSlot = new Slot(container, index, x, y) {
                            @Override
                            public boolean mayPlace(@NonNull ItemStack stack) {
                                return CurrencyValueService.isExchangeInput(registryAccess, owner, stack);
                            }
                        };
                        return this.inputSlot;
                    })
                    .container("result", this.result, (container, index, x, y) -> {
                        this.resultSlot = new Slot(container, index, x, y) {
                            @Override
                            public boolean mayPlace(@NonNull ItemStack stack) {
                                return false;
                            }

                            @Override
                            public void onTake(@NonNull Player player, @NonNull ItemStack stack) {
                                Setup.this.menu.takeResult(player, stack);
                                super.onTake(player, stack);
                            }
                        };
                        return this.resultSlot;
                    })
                    .player("inventory")
                    .build();
        }

        private void bindMenu(ExchangeStationMenu menu) {
            this.menu = menu;
        }

        private void inputChanged() {
            this.menu.slotsChanged(this.input);
            this.menu.updateListener.run();
        }

        private PageSlots.Layout page() {
            return this.page;
        }

        private SimpleContainer input() {
            return this.input;
        }

        private SimpleContainer result() {
            return this.result;
        }

        private Slot inputSlot() {
            return this.inputSlot;
        }

        private Slot resultSlot() {
            return this.resultSlot;
        }
    }
}
