package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.ChequeActionC2SPayload;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The cheque table: the fifteen currency cells, the two cheque cells and the two direction keys belong to the
 * bundled page, while the slots, their items and their tooltips stay vanilla. The two keys are the old
 * screen's {@code <} and {@code >} buttons, which only ever sent one packet each.
 */
public final class ChequeTableScreen extends AuiContainerScreen<ChequeTableMenu> {
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    private static final int CURRENCY_SLOTS = 15, CHEQUE_INPUT = CURRENCY_SLOTS, CHEQUE_OUTPUT = CHEQUE_INPUT + 1;

    public ChequeTableScreen(ChequeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.economyPage("cheque");
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");

        this.bindCells("currency", 0);
        this.bindCells("cheque_in", CHEQUE_INPUT);
        this.bindCells("cheque_out", CHEQUE_OUTPUT);
        this.bindInventoryCells("inventory");
        // The two keys kept their old literals and their old meanings: the top one cashes a cheque out, the
        // bottom one checks currency in.
        Element checkOut = this.getOrThrow("check_out");
        Element checkIn = this.getOrThrow("check_in");
        this.click(checkOut, () -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(false)));
        this.click(checkIn, () -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(true)));
        this.text(checkOut, Component.literal("<"));
        this.text(checkIn, Component.literal(">"));
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }
}
