package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.ChequeActionC2SPayload;
import com.iafenvoy.mxt.screen.AuiContainerScreen;
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import com.sighs.apricityui.init.Document;
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
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    private static final int CURRENCY_SLOTS = 15;
    private static final int CHEQUE_INPUT = CURRENCY_SLOTS;
    private static final int CHEQUE_OUTPUT = CHEQUE_INPUT + 1;

    public ChequeTableScreen(ChequeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.economyPage("cheque");
    }

    @Override
    protected String pageName() {
        return "cheque";
    }

    @Override
    protected boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element inventoryLabel = document.getElementById("inventory_label");
        if (inventoryLabel == null) return this.fail("inventory_label");
        Element checkOut = document.getElementById("check_out");
        if (checkOut == null) return this.fail("check_out");
        Element checkIn = document.getElementById("check_in");
        if (checkIn == null) return this.fail("check_in");
        this.panel = panel;
        if (!this.bindCells(document, "currency", 0)) return false;
        if (!this.bindCells(document, "cheque_in", CHEQUE_INPUT)) return false;
        if (!this.bindCells(document, "cheque_out", CHEQUE_OUTPUT)) return false;
        if (!this.bindInventoryCells(document, "inventory")) return false;
        // The two keys kept their old literals and their old meanings: the top one cashes a cheque out, the
        // bottom one checks currency in.
        this.click(checkOut, () -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(false)));
        this.click(checkIn, () -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(true)));
        this.text(title, this.getTitle());
        this.text(inventoryLabel, Component.translatable("container.inventory"));
        this.text(checkOut, Component.literal("<"));
        this.text(checkIn, Component.literal(">"));
        return true;
    }
}
