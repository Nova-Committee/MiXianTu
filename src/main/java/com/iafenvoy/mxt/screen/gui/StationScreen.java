package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.StationTradeC2SPayload;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * One client view for the four station menus: the two page layouts differ by the owner-only display and
 * stock cells and by the player inventory offset, everything else is shared.
 */
public final class StationScreen extends AuiContainerScreen<StationMenu> {
    private static final int PANEL_WIDTH = 176, CUSTOMER_HEIGHT = 167, OWNER_HEIGHT = 221;
    /**
     * The 4x3 cost and reward templates; {@link StationMenu} interleaves them, cost first.
     */
    private static final int TEMPLATE_CELLS = 12, TEMPLATES_END = TEMPLATE_CELLS * 2;
    private static final int OWNER_DISPLAY = TEMPLATES_END, OWNER_STOCK = TEMPLATES_END + 1;

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
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        // Both grids carry the same 0..11 local indices; the menu stores cost then reward per cell.
        this.bindCells("costs", index -> index * 2);
        this.bindCells("rewards", index -> index * 2 + 1);
        if (isOwner(this.menu)) {
            this.bindCells("display", OWNER_DISPLAY);
            this.bindCells("stock", OWNER_STOCK);
        } else
            this.click(this.getOrThrow("trade"), () -> ClientPacketDistributor.sendToServer(StationTradeC2SPayload.INSTANCE));
        this.bindInventoryCells("inventory");
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }
}
