package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.screen.EconomySlots.Display;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * One side of a server-owned, two-player item exchange.
 */
public final class PlayerTradeMenu extends ApricityContainerMenu {
    /**
     * One side's offer grid, which is also the size both of the page's offer containers are declared with.
     */
    private static final int OFFER_SLOTS = 20;
    private final Component partnerName;
    private final DataSlot partnerAccepted = DataSlot.standalone();
    // Both sides are read back from here rather than kept as screen state: a change to either offer has to be able
    // to clear a confirmation that was already given.
    private final DataSlot ownAccepted = DataSlot.standalone();

    public PlayerTradeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, new SimpleContainer(OFFER_SLOTS), new SimpleContainer(OFFER_SLOTS),
                ComponentSerialization.STREAM_CODEC.decode(buffer));
    }

    public PlayerTradeMenu(int containerId, Inventory inventory, Container ownOffer, Container partnerOffer, Component partnerName) {
        this(containerId, inventory, partnerName, new Setup(ownOffer, partnerOffer));
    }

    private PlayerTradeMenu(int containerId, Inventory inventory, Component partnerName, Setup setup) {
        super(containerId, inventory, setup.page().layout(), setup.page().sources(), Map.of(), null);
        checkContainerSize(setup.ownOffer(), OFFER_SLOTS);
        checkContainerSize(setup.partnerOffer(), OFFER_SLOTS);
        this.partnerName = partnerName;
        this.addDataSlot(this.partnerAccepted);
        this.addDataSlot(this.ownAccepted);
    }

    /**
     * The menu type ApricityUI's base menu would report is its own; the open packet carries whatever this answers,
     * and the client picks its screen factory from that.
     */
    @Override
    public @NonNull MenuType<?> getType() {
        return MxtMenus.PLAYER_TRADE.get();
    }

    public Component partnerName() {
        return this.partnerName;
    }

    public boolean partnerAccepted() {
        return this.partnerAccepted.get() != 0;
    }

    public boolean ownAccepted() {
        return this.ownAccepted.get() != 0;
    }

    public void setOwnAccepted(boolean accepted) {
        this.ownAccepted.set(accepted ? 1 : 0);
        this.broadcastChanges();
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
        if (index < OFFER_SLOTS) {
            if (!this.moveItemStackTo(slot.getItem(), OFFER_SLOTS * 2, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (index < OFFER_SLOTS * 2) {
            // The partner's grid is display-only, so a shift-click out of it never moves anything.
            return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), 0, OFFER_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        // Server only: the client's mirror menu belongs to a different instance and knows no session. On the
        // server a session whose menu is gone has nothing left to trade in, and answering false ends it.
        return !(player instanceof ServerPlayer serverPlayer) || PlayerTradeService.stillOpen(serverPlayer, this);
    }

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) PlayerTradeService.onMenuClosed(serverPlayer, this);
    }

    /**
     * The page's two offer containers and the layout they are opened with; the containers have to exist before the
     * menu, so the public constructors build this and hand it to the private one.
     */
    private static final class Setup {
        private final Container ownOffer;
        private final Container partnerOffer;
        private final PageSlots.Layout page;

        private Setup(Container ownOffer, Container partnerOffer) {
            this.ownOffer = ownOffer;
            this.partnerOffer = partnerOffer;
            this.page = PageSlots.of(AuiPages.economyPage("trade"))
                    .container("offer", ownOffer, Slot::new)
                    .container("partner", partnerOffer, Display::new)
                    .player("inventory")
                    .build();
        }

        private PageSlots.Layout page() {
            return this.page;
        }

        private Container ownOffer() {
            return this.ownOffer;
        }

        private Container partnerOffer() {
            return this.partnerOffer;
        }
    }
}
