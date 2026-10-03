package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.StationTradeC2SPayload;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * One client view for the four station menus: the trade owner's page adds the display and stock cells, the other
 * three share the customer page. The page and every cell come from {@link StationMenu}.
 */
public final class StationScreen extends AuiContainerScreen<StationMenu> {
    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        // Only the customer page has the trade key; the owner page has no such button.
        if (this.menu.mode() != Mode.TRADE_OWNER)
            this.click(this.getOrThrow("trade"), () -> ClientPacketDistributor.sendToServer(StationTradeC2SPayload.INSTANCE));
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }
}
