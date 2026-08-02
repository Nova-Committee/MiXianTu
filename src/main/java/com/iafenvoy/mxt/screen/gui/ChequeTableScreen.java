package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.ChequeActionC2SPayload;
import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
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
 * Client menu shell. The menu owns all currency and cheque state on the server.
 */
public final class ChequeTableScreen extends AbstractContainerScreen<ChequeTableMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/cheque_table.png");

    public ChequeTableScreen(ChequeTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(false)))
                .pos(this.leftPos + 104, this.topPos + 18).size(16, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> ClientPacketDistributor.sendToServer(new ChequeActionC2SPayload(true)))
                .pos(this.leftPos + 104, this.topPos + 54).size(16, 16).build());
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 256, 256);
    }
}
