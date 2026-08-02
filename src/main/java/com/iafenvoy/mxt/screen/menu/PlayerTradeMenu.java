package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.screen.EconomySlots.Display;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * One side of a server-owned, two-player item exchange.
 */
public final class PlayerTradeMenu extends AbstractContainerMenu {
    private final Component partnerName;
    private final DataSlot partnerAccepted = DataSlot.standalone();

    public PlayerTradeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, new SimpleContainer(20), new SimpleContainer(20), ComponentSerialization.STREAM_CODEC.decode(buffer));
    }

    public PlayerTradeMenu(int containerId, Inventory inventory, Container ownOffer, Container partnerOffer, Component partnerName) {
        super(MxtMenus.PLAYER_TRADE.get(), containerId);
        checkContainerSize(ownOffer, 20);
        checkContainerSize(partnerOffer, 20);
        this.partnerName = partnerName;
        this.addDataSlot(this.partnerAccepted);
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 4; column++) {
                int index = column + row * 4;
                this.addSlot(new Slot(ownOffer, index, 8 + column * 18, 18 + row * 18));
                this.addSlot(new Display(partnerOffer, index, 98 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
        for (int column = 0; column < 9; column++) this.addSlot(new Slot(inventory, column, 8 + column * 18, 198));
    }

    public Component partnerName() {
        return this.partnerName;
    }

    public boolean partnerAccepted() {
        return this.partnerAccepted.get() != 0;
    }

    public void setPartnerAccepted(boolean accepted) {
        this.partnerAccepted.set(accepted ? 1 : 0);
        this.broadcastChanges();
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < 40) {
            if ((index & 1) != 0 || !this.moveItemStackTo(slot.getItem(), 40, this.slots.size(), true))
                return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), 0, 40, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return true;
    }
}
