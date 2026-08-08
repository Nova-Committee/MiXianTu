package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.StationTradeC2SPayload;
import com.iafenvoy.mxt.screen.menu.StationMenu;
import com.iafenvoy.mxt.screen.menu.StationMenu.Mode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * Shared client view for the four station menus.
 */
public final class StationScreen extends AbstractContainerScreen<StationMenu> {
    private static final Identifier CUSTOMER_BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/trade_station_customer.png");
    private static final Identifier OWNER_BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/trade_station_owner.png");

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, menu.mode() == Mode.TRADE_OWNER ? 221 : 167);
    }

    @Override
    protected void init() {
        super.init();
        if (this.menu.isCustomer()) {
            this.addRenderableWidget(Button.builder(Component.literal("→"), button ->
                            ClientPacketDistributor.sendToServer(StationTradeC2SPayload.INSTANCE))
                    .pos(this.leftPos + 80, this.topPos + 35).size(16, 18).build());
        }
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED,
                this.menu.mode() == Mode.TRADE_OWNER ? OWNER_BACKGROUND : CUSTOMER_BACKGROUND,
                this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }
}
