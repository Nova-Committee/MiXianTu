package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.PlayerTradeAction;
import com.iafenvoy.mxt.network.payload.PlayerTradeActionC2SPayload;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
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
 * Client exchange screen for a direct player-to-player trade session.
 */
public final class PlayerTradeScreen extends AbstractContainerScreen<PlayerTradeMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/trade_command.png");
    private Button partner;
    private boolean accepted;

    public PlayerTradeScreen(PlayerTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 221);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mxt.player_trade.accept"), button -> {
            this.accepted = !this.accepted;
            ClientPacketDistributor.sendToServer(new PlayerTradeActionC2SPayload(
                    this.accepted ? PlayerTradeAction.ACCEPT : PlayerTradeAction.CANCEL_ACCEPT));
            button.setMessage(Component.translatable(this.accepted
                    ? "screen.mxt.player_trade.accepted" : "screen.mxt.player_trade.accept"));
        }).pos(this.leftPos + 7, this.topPos + 110).size(72, 16).build());
        this.partner = this.addRenderableWidget(Button.builder(Component.translatable("screen.mxt.player_trade.waiting"), button -> {
                })
                .pos(this.leftPos + 97, this.topPos + 110).size(72, 16).build());
        this.partner.active = false;
    }

    @Override
    public void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.partner != null) this.partner.setMessage(Component.translatable(this.menu.partnerAccepted()
                ? "screen.mxt.player_trade.accepted" : "screen.mxt.player_trade.waiting"));
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(this.font, this.menu.partnerName(), this.titleLabelX + 90, this.titleLabelY, 4210752, false);
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new PlayerTradeActionC2SPayload(PlayerTradeAction.CLOSE));
        super.onClose();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 256, 256);
    }
}
