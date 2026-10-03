package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload;
import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload.PlayerTradeAction;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * One side of a direct player-to-player trade: the two offer grids, the accept key, the partner's state and
 * the partner's name belong to the bundled page, while the slots, their items and their tooltips stay vanilla.
 * The old screen drew the 176x221 texture with two vanilla buttons; the page carries the same coordinates.
 */
public final class PlayerTradeScreen extends AuiContainerScreen<PlayerTradeMenu> {
    @Nullable
    private Element accept, partnerState;
    private boolean accepted;

    public PlayerTradeScreen(PlayerTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.accept = this.getOrThrow("accept");
        this.partnerState = this.getOrThrow("partner_state");

        this.click(this.accept, this::toggleAccept);
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("partner_name"), this.menu.partnerName());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }

    @Override
    public void onBindingsCleared() {
        this.accept = null;
        this.partnerState = null;
    }

    @Override
    public void onPageBound() {
        // Pushes the first state straight away, so neither key is painted in the wrong state for one tick.
        this.refresh();
    }

    @Override
    protected void refresh() {
        Element accept = this.accept;
        Element partnerState = this.partnerState;
        if (accept == null || partnerState == null) return;
        // The partner's side arrives through the menu's data slot, so it can change under this screen at any
        // tick; both writes compare before they touch the page.
        boolean partnerAccepted = this.menu.partnerAccepted();
        this.text(partnerState, Component.translatable(partnerAccepted
                ? "screen.mxt.player_trade.accepted" : "screen.mxt.player_trade.waiting"));
        AuiElements.setClass(partnerState, "accepted", partnerAccepted);
        this.text(accept, Component.translatable(this.accepted
                ? "screen.mxt.player_trade.accepted" : "screen.mxt.player_trade.accept"));
        AuiElements.setClass(accept, "accepted", this.accepted);
    }

    // The old screen flipped its own flag and told the server which of the two edges this was.
    private void toggleAccept() {
        this.accepted = !this.accepted;
        ClientPacketDistributor.sendToServer(new PlayerTradeActionC2SPayload(this.accepted
                ? PlayerTradeAction.ACCEPT : PlayerTradeAction.CANCEL_ACCEPT));
        this.refresh();
    }

    // Leaving is the one action the server cannot infer: the session ends when a side closes its menu.
    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new PlayerTradeActionC2SPayload(PlayerTradeAction.CLOSE));
        super.onClose();
    }
}