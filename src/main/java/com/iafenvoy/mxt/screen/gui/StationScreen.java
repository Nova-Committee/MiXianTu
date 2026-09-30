package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.StationTradeC2SPayload;
import com.iafenvoy.mxt.screen.AuiContainerScreen;
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * One client view for the four station menus: the two page layouts differ by the owner-only display and
 * stock cells and by the player inventory offset, everything else is shared.
 */
public final class StationScreen extends AuiContainerScreen<StationMenu> {
    private static final int PANEL_WIDTH = 176;
    private static final int CUSTOMER_HEIGHT = 167;
    private static final int OWNER_HEIGHT = 221;
    /**
     * The 4x3 cost and reward templates; {@link StationMenu} interleaves them, cost first.
     */
    private static final int TEMPLATE_CELLS = 12;
    private static final int TEMPLATES_END = TEMPLATE_CELLS * 2;
    private static final int OWNER_DISPLAY = TEMPLATES_END;
    private static final int OWNER_STOCK = TEMPLATES_END + 1;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, isOwner(menu) ? OWNER_HEIGHT : CUSTOMER_HEIGHT);
    }

    private static boolean isOwner(StationMenu menu) {
        return menu.mode() == Mode.TRADE_OWNER;
    }

    @Override
    protected String pagePath() {
        return AuiPages.economyPage(this.pageName());
    }

    @Override
    protected String pageName() {
        return isOwner(this.menu) ? "station_owner" : "station_customer";
    }

    @Override
    protected boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element inventoryLabel = document.getElementById("inventory_label");
        if (inventoryLabel == null) return this.fail("inventory_label");
        this.panel = panel;
        // Both grids carry the same 0..11 local indices; the menu stores cost then reward per cell.
        if (!this.bindCells(document, "costs", index -> index * 2)) return false;
        if (!this.bindCells(document, "rewards", index -> index * 2 + 1)) return false;
        if (isOwner(this.menu)) {
            if (!this.bindCells(document, "display", OWNER_DISPLAY)) return false;
            if (!this.bindCells(document, "stock", OWNER_STOCK)) return false;
        } else {
            Element trade = document.getElementById("trade");
            if (trade == null) return this.fail("trade");
            this.click(trade, () -> ClientPacketDistributor.sendToServer(StationTradeC2SPayload.INSTANCE));
        }
        if (!this.bindInventoryCells(document, "inventory")) return false;
        this.text(title, this.getTitle());
        this.text(inventoryLabel, Component.translatable("container.inventory"));
        return true;
    }
}
