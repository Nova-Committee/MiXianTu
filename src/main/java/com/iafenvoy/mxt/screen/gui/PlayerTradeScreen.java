package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload;
import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload.PlayerTradeAction;
import com.iafenvoy.mxt.screen.AuiContainerScreen;
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import com.sighs.apricityui.init.Document;
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
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 221;
    /**
     * The menu interleaves the two offer grids rather than listing them one after the other: own cell
     * {@code i} is menu slot {@code 2i} and the partner's display cell is {@code 2i + 1}. The page numbers its
     * cells 0..19 per grid, so both mappings are these two constants - the page contract names nothing else.
     */
    private static final int OFFER_STRIDE = 2;
    private static final int PARTNER_OFFSET = 1;

    @Nullable
    private Element accept;
    @Nullable
    private Element partnerState;
    private boolean accepted;

    public PlayerTradeScreen(PlayerTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.economyPage("trade");
    }

    @Override
    protected String pageName() {
        return "trade";
    }

    @Override
    protected boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element partnerName = document.getElementById("partner_name");
        if (partnerName == null) return this.fail("partner_name");
        Element inventoryLabel = document.getElementById("inventory_label");
        if (inventoryLabel == null) return this.fail("inventory_label");
        Element accept = document.getElementById("accept");
        if (accept == null) return this.fail("accept");
        Element partnerState = document.getElementById("partner_state");
        if (partnerState == null) return this.fail("partner_state");
        this.panel = panel;
        if (!this.bindCells(document, "offer", index -> index * OFFER_STRIDE)) return false;
        if (!this.bindCells(document, "partner", index -> index * OFFER_STRIDE + PARTNER_OFFSET)) return false;
        if (!this.bindInventoryCells(document, "inventory")) return false;
        this.accept = accept;
        this.partnerState = partnerState;
        this.click(accept, this::toggleAccept);
        this.text(title, this.getTitle());
        this.text(partnerName, this.menu.partnerName());
        this.text(inventoryLabel, Component.translatable("container.inventory"));
        return true;
    }

    @Override
    protected void onPageBound() {
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
        setClass(partnerState, "accepted", partnerAccepted);
        this.text(accept, Component.translatable(this.accepted
                ? "screen.mxt.player_trade.accepted" : "screen.mxt.player_trade.accept"));
        setClass(accept, "accepted", this.accepted);
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
